package org.shadowgrove.passporta.data.security

import android.content.Context
import android.util.Log
import java.nio.ByteBuffer
import java.nio.CharBuffer

/**
 * Persists the optional backup password, only ever in Keystore-encrypted form.
 *
 * It's needed in recoverable form (not as a hash) because automatic backups run unattended.
 */
class BackupPasswordStore(context: Context) {

    /** Result of loading the stored password. */
    sealed interface StoredPassword {
        /** Backups are not password-protected. */
        data object None : StoredPassword

        /** The caller owns [chars] and must wipe them after use. */
        class Available(val chars: CharArray) : StoredPassword

        /**
         * A password is configured but can't be decrypted. Callers must fail closed - never fall
         * back to writing an unencrypted backup.
         */
        data object Unavailable : StoredPassword
    }

    private val secretStore = AndroidKeystoreStore(
        context = context,
        alias = KEY_ALIAS,
        preferencesName = PREFERENCES_NAME,
    )

    /** Cheap check without a Keystore operation - safe to call on the main thread. */
    fun isConfigured(): Boolean = secretStore.contains()

    /** Decrypts the stored password. Performs a Keystore operation: call off the main thread. */
    fun load(): StoredPassword {
        val result = try {
            secretStore.read()
        } catch (error: Exception) {
            Log.w(TAG, "Backup password could not be read", error)
            return StoredPassword.Unavailable
        }
        return when (result) {
            AndroidKeystoreStore.ReadResult.Missing -> StoredPassword.None
            AndroidKeystoreStore.ReadResult.Unrecoverable -> StoredPassword.Unavailable
            is AndroidKeystoreStore.ReadResult.Value -> try {
                val decoded = Charsets.UTF_8.decode(ByteBuffer.wrap(result.bytes))
                val chars = CharArray(decoded.remaining()).also { decoded.get(it) }
                decoded.wipe()
                if (chars.isEmpty()) StoredPassword.Unavailable else StoredPassword.Available(chars)
            } finally {
                result.bytes.fill(0)
            }
        }
    }

    /**
     * Stores [password] and wipes the given array afterwards.
     *
     * @throws IllegalArgumentException if the password is shorter than [MIN_PASSWORD_LENGTH].
     */
    fun set(password: CharArray) {
        try {
            require(password.size >= MIN_PASSWORD_LENGTH) { "Password is too short" }
            val encoded = Charsets.UTF_8.encode(CharBuffer.wrap(password))
            val bytes = ByteArray(encoded.remaining()).also { encoded.get(it) }
            encoded.wipe()
            try {
                secretStore.write(bytes)
            } finally {
                bytes.fill(0)
            }
        } finally {
            password.fill('\u0000')
        }
    }

    fun clear() = secretStore.clear()

    private fun ByteBuffer.wipe() {
        if (hasArray()) array().fill(0)
    }

    private fun CharBuffer.wipe() {
        if (hasArray()) array().fill('\u0000')
    }

    companion object {
        const val MIN_PASSWORD_LENGTH = 12
        private const val TAG = "BackupPasswordStore"
        private const val KEY_ALIAS = "passporta.backup.password"
        private const val PREFERENCES_NAME = "passporta_backup_secret"
    }
}


