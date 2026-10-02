package org.shadowgrove.passporta.data.security

import android.content.Context
import android.util.Log
import java.security.SecureRandom

/**
 * Creates and persists the random 256-bit SQLCipher key, wrapped by the Android Keystore.
 *
 * The key is independent of any user password: the database stays encrypted at rest without
 * user interaction, and changing or removing the backup password never touches it.
 */
class DatabaseKeyStore(context: Context) {

    private val secretStore = AndroidKeystoreStore(
        context = context,
        alias = KEY_ALIAS,
        preferencesName = PREFERENCES_NAME,
    )

    /**
     * Returns the stored key or creates (and durably stores) a new one.
     *
     * If the stored key is permanently lost, a new key is created; the database opener then
     * detects that the existing file can't be decrypted and sets it aside instead of deleting it.
     */
    fun getOrCreate(): ByteArray {
        when (val stored = secretStore.read()) {
            is AndroidKeystoreStore.ReadResult.Value -> {
                if (stored.bytes.size == KEY_BYTES) return stored.bytes
                stored.bytes.fill(0)
                Log.e(TAG, "Stored database key has an invalid length; creating a new key")
                secretStore.clear()
            }
            AndroidKeystoreStore.ReadResult.Unrecoverable -> {
                Log.e(TAG, "Stored database key can't be recovered from the Android Keystore")
                secretStore.clear()
            }
            AndroidKeystoreStore.ReadResult.Missing -> Unit
        }
        return ByteArray(KEY_BYTES).also { key ->
            SecureRandom().nextBytes(key)
            secretStore.write(key)
        }
    }

    private companion object {
        const val TAG = "DatabaseKeyStore"
        const val KEY_ALIAS = "passporta.database.key"
        const val PREFERENCES_NAME = "passporta_database_secret"
        const val KEY_BYTES = 32
    }
}

