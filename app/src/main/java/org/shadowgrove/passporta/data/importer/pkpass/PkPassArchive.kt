package org.shadowgrove.passporta.data.importer.pkpass

import android.util.Log
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.shadowgrove.passporta.data.importer.ImportJson
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * In-memory content of a `.pkpass` archive.
 *
 * A pass is typically well under 1 MB, so reading it in one go is unproblematic and avoids
 * temporary files.
 *
 * Security: entry names are checked against path traversal ("Zip Slip"); [MAX_ENTRY_BYTES] and
 * [MAX_TOTAL_BYTES] also limit the damage from zip bombs.
 */
internal class PkPassArchive private constructor(
    private val entries: Map<String, ByteArray>,
) {

    val entryNames: Set<String> get() = entries.keys

    /** Entry by exact name (case is ignored). */
    operator fun get(name: String): ByteArray? = entries[name.lowercase()]

    /** First existing entry from [names] - for priority lists like `logo@2x.png`, `logo.png`. */
    fun firstOf(vararg names: String): ByteArray? = names.firstNotNullOfOrNull { get(it) }

    /** All entries whose name ends with [suffix]. */
    fun endingWith(suffix: String): Map<String, ByteArray> =
        entries.filterKeys { it.endsWith(suffix.lowercase()) }

    /**
     * Verifies the SHA-1 checksums from `manifest.json`.
     *
     * This does not replace signature verification (which would need Apple's WWDR certificate
     * chain), but reliably detects damaged or subsequently modified archives - fully offline.
     *
     * @return `true` if all files listed in the manifest and present in the archive match.
     */
    fun verifyIntegrity(): Boolean {
        val manifestBytes = get(MANIFEST_ENTRY) ?: return false
        val manifest = runCatching {
            ImportJson.parseToJsonElement(manifestBytes.toString(Charsets.UTF_8))
        }.getOrNull() as? JsonObject ?: return false

        var checked = 0
        manifest.forEach { (fileName, hashElement) ->
            val expected = (hashElement as? JsonPrimitive)?.content ?: return@forEach
            val actualBytes = get(fileName) ?: return@forEach
            checked++
            if (!sha1Hex(actualBytes).equals(expected, ignoreCase = true)) {
                Log.w(TAG, "Checksum of $fileName does not match")
                return false
            }
        }
        return checked > 0
    }

    private fun sha1Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-1")
            .digest(bytes)
            .joinToString(separator = "") { "%02x".format(it) }

    companion object {

        private const val TAG = "PkPassArchive"

        const val MANIFEST_ENTRY = "manifest.json"
        const val PASS_ENTRY = "pass.json"

        /** Largest accepted single image or JSON. */
        private const val MAX_ENTRY_BYTES = 8L * 1024 * 1024

        /** Upper limit for the entire unpacked archive. */
        private const val MAX_TOTAL_BYTES = 32L * 1024 * 1024

        private const val COPY_BUFFER_BYTES = 16 * 1024

        /**
         * Reads the archive from [input]. The stream is fully consumed but not closed - the
         * caller takes care of that (`use { }`).
         */
        fun read(input: InputStream): PkPassArchive {
            val result = mutableMapOf<String, ByteArray>()
            var totalBytes = 0L

            val zip = ZipInputStream(input.buffered())
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                if (!entry.isDirectory && isSafeEntryName(name)) {
                    val bytes = zip.readLimited(MAX_ENTRY_BYTES)
                    if (bytes != null) {
                        totalBytes += bytes.size
                        require(totalBytes <= MAX_TOTAL_BYTES) {
                            "Archive exceeds the size limit"
                        }
                        result[name.lowercase()] = bytes
                    } else {
                        Log.w(TAG, "Entry $name skipped (too large)")
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
            return PkPassArchive(result)
        }

        /** Prevents path traversal and absolute paths. */
        private fun isSafeEntryName(name: String): Boolean {
            if (name.isBlank()) return false
            if (name.startsWith("/") || name.startsWith("\\")) return false
            if (name.contains("\\")) return false
            return name.split('/').none { it == ".." }
        }

        /** Reads at most [limit] bytes; returns `null` if the entry is larger. */
        private fun InputStream.readLimited(limit: Long): ByteArray? {
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(COPY_BUFFER_BYTES)
            var total = 0L
            while (true) {
                val read = read(buffer)
                if (read == -1) break
                total += read
                if (total > limit) return null
                output.write(buffer, 0, read)
            }
            return output.toByteArray()
        }
    }
}
