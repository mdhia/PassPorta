package org.shadowgrove.passporta.data.security

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Password-based, authenticated streaming encryption for PassPorta backup files.
 *
 * Format (all integers big-endian):
 * ```
 * header  = magic "PPBACKUP" | version (1) | kdf id (1) | iterations (4) | salt (16)
 *           | segment size (4) | nonce prefix (7)
 * body    = segment_0 | segment_1 | ... | segment_n
 * segment = AES-256-GCM(plaintext chunk) incl. 16-byte tag
 * nonce_i = nonce prefix (7) | i (4) | 1 if last segment else 0 (1)
 * ```
 * - Key: PBKDF2-HMAC-SHA256 (600 000 iterations, random 128-bit salt per file).
 * - The complete header is authenticated as AAD of every segment, so KDF parameters can't be
 *   tampered with unnoticed.
 * - Segment counter and last-segment flag in the nonce detect reordering, removal and
 *   truncation of segments (the "STREAM" construction, as used by Tink's streaming AEAD).
 * - Only one segment is held in memory, so large backups don't exhaust the heap.
 */
object BackupCrypto {

    /** Number of leading bytes needed to recognize an encrypted backup, see [hasMagic]. */
    const val MAGIC_BYTES = 8

    internal const val PBKDF2_ITERATIONS = 600_000
    internal const val DEFAULT_SEGMENT_BYTES = 1 shl 20
    internal const val MIN_SEGMENT_BYTES = 1 shl 12

    private val MAGIC = "PPBACKUP".toByteArray(Charsets.US_ASCII)
    private const val VERSION: Byte = 2
    private const val KDF_PBKDF2_HMAC_SHA256: Byte = 1
    private const val SALT_BYTES = 16
    private const val NONCE_PREFIX_BYTES = 7
    private const val NONCE_BYTES = 12
    private const val TAG_BYTES = 16
    private const val MIN_ITERATIONS = 100_000
    private const val MAX_ITERATIONS = 10_000_000
    private const val MAX_SEGMENT_BYTES = 1 shl 24
    private const val MAX_SEGMENTS = 1L shl 32
    private const val HEADER_BYTES =
        MAGIC_BYTES + 1 + 1 + Int.SIZE_BYTES + SALT_BYTES + Int.SIZE_BYTES + NONCE_PREFIX_BYTES
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    /** `true` if [prefix] starts with the magic bytes of an encrypted PassPorta backup. */
    fun hasMagic(prefix: ByteArray): Boolean =
        prefix.size >= MAGIC_BYTES && MAGIC.indices.all { prefix[it] == MAGIC[it] }

    /**
     * Writes the header to [output] and returns a stream that encrypts everything written to it.
     * Closing the returned stream writes the final segment and closes [output]; a backup that
     * was never closed is detected as truncated on decryption.
     */
    fun encryptingStream(
        output: OutputStream,
        password: CharArray,
        segmentBytes: Int = DEFAULT_SEGMENT_BYTES,
    ): OutputStream {
        require(segmentBytes in MIN_SEGMENT_BYTES..MAX_SEGMENT_BYTES) { "Invalid segment size" }
        val random = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val noncePrefix = ByteArray(NONCE_PREFIX_BYTES).also(random::nextBytes)
        val header = ByteBuffer.allocate(HEADER_BYTES)
            .put(MAGIC)
            .put(VERSION)
            .put(KDF_PBKDF2_HMAC_SHA256)
            .putInt(PBKDF2_ITERATIONS)
            .put(salt)
            .putInt(segmentBytes)
            .put(noncePrefix)
            .array()
        val key = aesKey(password, salt, PBKDF2_ITERATIONS)
        output.write(header)
        return EncryptingOutputStream(output, key, header, noncePrefix, segmentBytes)
    }

    /**
     * Reads and validates the header from [input] and returns a stream of the decrypted content.
     *
     * Every segment is authenticated before any of its bytes are returned.
     *
     * @throws InvalidBackupException if the header is malformed or unsupported.
     * The returned stream throws [InvalidBackupPasswordException] if the first segment can't be
     * authenticated (wrong password) and [InvalidBackupException] for later damage/truncation.
     */
    fun decryptingStream(input: InputStream, password: CharArray): InputStream {
        val header = ByteArray(HEADER_BYTES)
        if (input.readFully(header) != HEADER_BYTES || !hasMagic(header)) {
            throw InvalidBackupException("Not an encrypted PassPorta backup")
        }
        val buffer = ByteBuffer.wrap(header, MAGIC_BYTES, HEADER_BYTES - MAGIC_BYTES)
        val version = buffer.get()
        val kdf = buffer.get()
        if (version != VERSION || kdf != KDF_PBKDF2_HMAC_SHA256) {
            throw InvalidBackupException("Unsupported backup encryption format")
        }
        val iterations = buffer.getInt()
        val salt = ByteArray(SALT_BYTES).also { buffer.get(it) }
        val segmentBytes = buffer.getInt()
        val noncePrefix = ByteArray(NONCE_PREFIX_BYTES).also { buffer.get(it) }
        if (iterations !in MIN_ITERATIONS..MAX_ITERATIONS ||
            segmentBytes !in MIN_SEGMENT_BYTES..MAX_SEGMENT_BYTES
        ) {
            throw InvalidBackupException("Invalid encryption parameters")
        }
        val key = aesKey(password, salt, iterations)
        return DecryptingInputStream(input, key, header, noncePrefix, segmentBytes)
    }

    /** PBKDF2-HMAC-SHA256 with a 32-byte output (exactly one PBKDF2 block). */
    internal fun pbkdf2HmacSha256(password: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        require(password.isNotEmpty()) { "Password must not be empty" }
        require(iterations >= 1) { "Iterations must be positive" }
        val encoded = Charsets.UTF_8.encode(CharBuffer.wrap(password))
        val passwordBytes = ByteArray(encoded.remaining()).also { encoded.get(it) }
        if (encoded.hasArray()) encoded.array().fill(0)
        try {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(passwordBytes, "HmacSHA256"))
            mac.update(salt)
            mac.update(byteArrayOf(0, 0, 0, 1))
            val block = mac.doFinal()
            val result = block.copyOf()
            repeat(iterations - 1) {
                mac.update(block)
                mac.doFinal(block, 0)
                for (index in result.indices) {
                    result[index] = (result[index].toInt() xor block[index].toInt()).toByte()
                }
            }
            block.fill(0)
            return result
        } finally {
            passwordBytes.fill(0)
        }
    }

    private fun aesKey(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val raw = pbkdf2HmacSha256(password, salt, iterations)
        return try {
            SecretKeySpec(raw, "AES")
        } finally {
            raw.fill(0)
        }
    }

    private fun nonce(prefix: ByteArray, counter: Long, last: Boolean): ByteArray =
        ByteBuffer.allocate(NONCE_BYTES)
            .put(prefix)
            .putInt(counter.toInt())
            .put(if (last) 1 else 0)
            .array()

    private class EncryptingOutputStream(
        private val output: OutputStream,
        private val key: SecretKeySpec,
        private val header: ByteArray,
        private val noncePrefix: ByteArray,
        segmentBytes: Int,
    ) : OutputStream() {

        private val cipher = Cipher.getInstance(TRANSFORMATION)
        private val buffer = ByteArray(segmentBytes)
        private var filled = 0
        private var counter = 0L
        private var closed = false

        override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)

        override fun write(b: ByteArray, off: Int, len: Int) {
            if (closed) throw IOException("Stream closed")
            if (off < 0 || len < 0 || off + len > b.size) throw IndexOutOfBoundsException()
            var offset = off
            var remaining = len
            while (remaining > 0) {
                // A full segment is only written once more data follows, so the final segment -
                // flagged in its nonce - is always the one written by close().
                if (filled == buffer.size) writeSegment(last = false)
                val count = minOf(remaining, buffer.size - filled)
                System.arraycopy(b, offset, buffer, filled, count)
                filled += count
                offset += count
                remaining -= count
            }
        }

        override fun flush() = output.flush()

        override fun close() {
            if (closed) return
            closed = true
            try {
                writeSegment(last = true)
                output.flush()
            } finally {
                buffer.fill(0)
                output.close()
            }
        }

        private fun writeSegment(last: Boolean) {
            if (counter >= MAX_SEGMENTS) throw IOException("Backup too large")
            try {
                cipher.init(
                    Cipher.ENCRYPT_MODE,
                    key,
                    GCMParameterSpec(TAG_BYTES * 8, nonce(noncePrefix, counter, last)),
                )
                cipher.updateAAD(header)
                output.write(cipher.doFinal(buffer, 0, filled))
            } catch (error: GeneralSecurityException) {
                throw IOException("Encryption failed", error)
            }
            counter++
            filled = 0
        }
    }

    private class DecryptingInputStream(
        private val input: InputStream,
        private val key: SecretKeySpec,
        private val header: ByteArray,
        private val noncePrefix: ByteArray,
        segmentBytes: Int,
    ) : InputStream() {

        private val cipher = Cipher.getInstance(TRANSFORMATION)
        private val encryptedSegmentBytes = segmentBytes + TAG_BYTES
        private var nextSegment: ByteArray? = null
        private var started = false
        private var finished = false
        private var plain = ByteArray(0)
        private var position = 0
        private var counter = 0L

        override fun read(): Int {
            val single = ByteArray(1)
            return if (read(single, 0, 1) == -1) -1 else single[0].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (off < 0 || len < 0 || off + len > b.size) throw IndexOutOfBoundsException()
            if (len == 0) return 0
            while (position >= plain.size) {
                if (!decryptNextSegment()) return -1
            }
            val count = minOf(len, plain.size - position)
            System.arraycopy(plain, position, b, off, count)
            position += count
            return count
        }

        override fun close() {
            plain.fill(0)
            input.close()
        }

        private fun decryptNextSegment(): Boolean {
            if (finished) return false
            if (!started) {
                started = true
                nextSegment = readSegment()
            }
            val segment = nextSegment ?: throw InvalidBackupException("Encrypted backup is truncated")
            // A segment is the last one if no further data follows it.
            val following = if (segment.size == encryptedSegmentBytes) readSegment() else null
            val last = following == null
            if (segment.size < TAG_BYTES || counter >= MAX_SEGMENTS) {
                throw InvalidBackupException("Encrypted backup is truncated")
            }

            plain.fill(0)
            plain = try {
                cipher.init(
                    Cipher.DECRYPT_MODE,
                    key,
                    GCMParameterSpec(TAG_BYTES * 8, nonce(noncePrefix, counter, last)),
                )
                cipher.updateAAD(header)
                cipher.doFinal(segment)
            } catch (error: GeneralSecurityException) {
                if (counter == 0L) throw InvalidBackupPasswordException(error)
                throw InvalidBackupException("Encrypted backup is damaged", error)
            }
            position = 0
            counter++
            nextSegment = following
            finished = last
            return true
        }

        /** Next encrypted segment, shorter only at the end of the stream; `null` at EOF. */
        private fun readSegment(): ByteArray? {
            val segment = ByteArray(encryptedSegmentBytes)
            val read = input.readFully(segment)
            return when (read) {
                0 -> null
                segment.size -> segment
                else -> segment.copyOf(read)
            }
        }
    }

    /** Reads until [buffer] is full or the stream ends; returns the number of bytes read. */
    private fun InputStream.readFully(buffer: ByteArray): Int {
        var total = 0
        while (total < buffer.size) {
            val read = read(buffer, total, buffer.size - total)
            if (read == -1) break
            total += read
        }
        return total
    }

    /** The file is not a (supported, intact) encrypted backup. */
    class InvalidBackupException(message: String, cause: Throwable? = null) : IOException(message, cause)

    /** The password is wrong - or the very first segment was tampered with. */
    class InvalidBackupPasswordException(cause: Throwable) :
        IOException("Backup password is invalid", cause)
}

