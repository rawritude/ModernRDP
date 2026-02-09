package com.modernrdp.data.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encrypts/decrypts credentials using Android Keystore.
 *
 * Uses AES-256-GCM with a hardware-backed key that never leaves the secure element.
 * The IV is prepended to the ciphertext and stored together in Base64.
 */
@Singleton
class CredentialEncryption @Inject constructor() {

    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "modernrdp_credentials"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
        private const val ENCRYPTED_PREFIX = "enc:"
    }

    private val keyStore: KeyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }

    private fun getOrCreateKey(): SecretKey {
        keyStore.getEntry(KEY_ALIAS, null)?.let { entry ->
            return (entry as KeyStore.SecretKeyEntry).secretKey
        }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            KEYSTORE_PROVIDER,
        )
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(false) // Don't require biometric for each use
                .build(),
        )
        return keyGenerator.generateKey()
    }

    /**
     * Encrypt a plaintext credential. Returns a Base64 string prefixed with "enc:".
     * If the input is empty, returns it unchanged.
     */
    fun encrypt(plaintext: String): String {
        if (plaintext.isBlank()) return plaintext

        val key = getOrCreateKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)

        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        // Prepend IV to ciphertext: [iv_len(1)][iv][ciphertext]
        val combined = ByteArray(1 + iv.size + ciphertext.size)
        combined[0] = iv.size.toByte()
        System.arraycopy(iv, 0, combined, 1, iv.size)
        System.arraycopy(ciphertext, 0, combined, 1 + iv.size, ciphertext.size)

        return ENCRYPTED_PREFIX + Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    /**
     * Decrypt a credential string. If it doesn't have the "enc:" prefix,
     * returns as-is (legacy plaintext).
     */
    fun decrypt(encrypted: String): String {
        if (!encrypted.startsWith(ENCRYPTED_PREFIX)) return encrypted
        if (encrypted == ENCRYPTED_PREFIX) return ""

        return try {
            val combined = Base64.decode(encrypted.removePrefix(ENCRYPTED_PREFIX), Base64.NO_WRAP)

            val ivLen = combined[0].toInt() and 0xFF
            val iv = combined.copyOfRange(1, 1 + ivLen)
            val ciphertext = combined.copyOfRange(1 + ivLen, combined.size)

            val key = getOrCreateKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))

            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (e: Exception) {
            // If decryption fails (key rotated, corrupted), return empty
            ""
        }
    }

    /**
     * Check if a string is already encrypted.
     */
    fun isEncrypted(value: String): Boolean = value.startsWith(ENCRYPTED_PREFIX)
}
