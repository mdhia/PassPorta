package org.shadowgrove.passporta.data.security

import android.annotation.SuppressLint
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import java.security.KeyStore
import java.security.UnrecoverableKeyException
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores one small secret in private SharedPreferences, encrypted with AES-256-GCM under a
 * non-exportable Android Keystore key.
 *
 * Reading never creates a Keystore key: if the key vanished, the stored ciphertext is reported
 * as [ReadResult.Unrecoverable] instead of being "replaced" by a fresh key that can never
 * decrypt it.
 */
class AndroidKeystoreStore(
    context: Context,
    private val alias: String,
    preferencesName: String,
) {

    sealed interface ReadResult {
        /** Nothing has been stored yet. */
        data object Missing : ReadResult

        class Value(val bytes: ByteArray) : ReadResult

        /** Something is stored, but it can never be decrypted again on this device. */
        data object Unrecoverable : ReadResult
    }

    private val preferences = context.applicationContext
        .getSharedPreferences(preferencesName, Context.MODE_PRIVATE)

    fun contains(): Boolean = preferences.contains(KEY_CIPHERTEXT) || preferences.contains(KEY_NONCE)

    /**
     * Decrypts the stored secret.
     *
     * Transient Keystore failures are rethrown, so callers never mistake a temporary problem for
     * permanent key loss.
     */
    fun read(): ReadResult {
        val encodedCiphertext = preferences.getString(KEY_CIPHERTEXT, null)
        val encodedNonce = preferences.getString(KEY_NONCE, null)
        if (encodedCiphertext == null && encodedNonce == null) return ReadResult.Missing
        if (encodedCiphertext == null || encodedNonce == null) return ReadResult.Unrecoverable

        val key = existingKey() ?: return ReadResult.Unrecoverable
        val nonce: ByteArray
        val ciphertext: ByteArray
        try {
            nonce = Base64.decode(encodedNonce, Base64.NO_WRAP)
            ciphertext = Base64.decode(encodedCiphertext, Base64.NO_WRAP)
        } catch (_: IllegalArgumentException) {
            return ReadResult.Unrecoverable
        }

        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce))
            ReadResult.Value(cipher.doFinal(ciphertext))
        } catch (_: KeyPermanentlyInvalidatedException) {
            ReadResult.Unrecoverable
        } catch (_: AEADBadTagException) {
            ReadResult.Unrecoverable
        }
    }

    /**
     * Encrypts and persists [secret] synchronously.
     *
     * `commit()` instead of `apply()`: callers such as the database key must not continue before
     * the secret is durably stored, otherwise a process death could lose a key already in use.
     */
    @SuppressLint("ApplySharedPref")
    fun write(secret: ByteArray) {
        // Android Keystore keys only accept provider-generated IVs for encryption; the IV is
        // stored next to the ciphertext.
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(secret)
        val committed = preferences.edit()
            .putString(KEY_NONCE, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString(KEY_CIPHERTEXT, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            .commit()
        check(committed) { "Encrypted secret could not be persisted" }
    }

    /** Removes the stored secret and its Keystore key. */
    fun clear() {
        preferences.edit(commit = true) {
            remove(KEY_NONCE)
            remove(KEY_CIPHERTEXT)
        }
        runCatching { keyStore().deleteEntry(alias) }
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun existingKey(): SecretKey? = try {
        keyStore().getKey(alias, null) as? SecretKey
    } catch (_: UnrecoverableKeyException) {
        null
    }

    private fun getOrCreateKey(): SecretKey = existingKey() ?: KeyGenerator
        .getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        .apply {
            init(
                KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(KEY_BITS)
                    .build(),
            )
        }
        .generateKey()

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_BITS = 256
        const val TAG_BITS = 128
        const val KEY_NONCE = "nonce"
        const val KEY_CIPHERTEXT = "ciphertext"
    }
}



