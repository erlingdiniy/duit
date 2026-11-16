package com.diniy.duit.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec

/**
 * Seals and unseals already-salted KDF PIN records at rest.
 * In production, backed by an AES-256-GCM key inside AndroidKeyStore.
 */
interface SecretProtector {
    fun seal(plaintext: String): String
    fun unseal(ciphertext: String): String?
}

class AndroidKeyStoreSecretProtector(
    private val keyAlias: String = "duit_pin_envelope_v1"
) : SecretProtector {

    override fun seal(plaintext: String): String {
        val secretKey = getOrCreateSecretKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))
        val encoder = Base64.getUrlEncoder().withoutPadding()
        return "$ENVELOPE_PREFIX|${encoder.encodeToString(iv)}|${encoder.encodeToString(encrypted)}"
    }

    override fun unseal(ciphertext: String): String? {
        return runCatching {
            val parts = ciphertext.split("|")
            if (parts.size != 3 || parts[0] != ENVELOPE_PREFIX) return null
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val secretKey = keyStore.getKey(keyAlias, null) as? SecretKey ?: return null
            val iv = Base64.getUrlDecoder().decode(parts[1])
            val encrypted = Base64.getUrlDecoder().decode(parts[2])
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(encrypted), StandardCharsets.UTF_8)
        }.getOrNull()
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(keyAlias, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val ENVELOPE_PREFIX = "aks1"
    }
}

/**
 * Cryptographic helper for 6-digit app PINs:
 * - Strict 6 ASCII digit validation.
 * - Per-PIN 128-bit SecureRandom salt + PBKDF2WithHmacSHA256 (120,000 iterations, 256-bit key).
 * - Sealed inside [SecretProtector] (Android Keystore AES-GCM in production).
 * - Constant-time byte comparison via [MessageDigest.isEqual].
 * Never persists or logs plaintext PINs.
 */
class PinHashEngine(
    private val secretProtector: SecretProtector,
    private val iterations: Int = DEFAULT_ITERATIONS,
    private val random: SecureRandom = SecureRandom()
) {

    fun isValidPinFormat(pin: String): Boolean =
        pin.length == PIN_LENGTH && pin.all { it in '0'..'9' }

    fun createEncryptedEnvelope(pin: CharArray): String {
        require(pin.size == PIN_LENGTH && pin.all { it in '0'..'9' }) {
            "PIN must contain exactly $PIN_LENGTH digits."
        }
        val salt = ByteArray(SALT_BYTES).also { random.nextBytes(it) }
        val derived = deriveKey(pin, salt, iterations)
        return try {
            val encoder = Base64.getUrlEncoder().withoutPadding()
            val payload = "$RECORD_PREFIX|$iterations|${encoder.encodeToString(salt)}|${encoder.encodeToString(derived)}"
            secretProtector.seal(payload)
        } finally {
            derived.fill(0)
        }
    }

    fun verifyPin(pin: CharArray, encryptedEnvelope: String?): Boolean {
        if (encryptedEnvelope.isNullOrBlank()) return false
        if (pin.size != PIN_LENGTH || !pin.all { it in '0'..'9' }) return false

        val payload = secretProtector.unseal(encryptedEnvelope) ?: return false
        val parts = payload.split("|")
        if (parts.size != 4 || parts[0] != RECORD_PREFIX) return false

        val iterCount = parts[1].toIntOrNull()?.takeIf { it >= 10_000 } ?: return false
        val salt = runCatching { Base64.getUrlDecoder().decode(parts[2]) }.getOrNull() ?: return false
        val expectedHash = runCatching { Base64.getUrlDecoder().decode(parts[3]) }.getOrNull() ?: return false

        val actualHash = deriveKey(pin, salt, iterCount)
        return try {
            MessageDigest.isEqual(expectedHash, actualHash)
        } finally {
            actualHash.fill(0)
        }
    }

    private fun deriveKey(pin: CharArray, salt: ByteArray, iterCount: Int): ByteArray {
        val spec = PBEKeySpec(pin, salt, iterCount, KEY_BITS)
        return try {
            val factory = SecretKeyFactory.getInstance(KDF_ALGORITHM)
            factory.generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    companion object {
        const val PIN_LENGTH = 6
        const val DEFAULT_ITERATIONS = 120_000
        private const val SALT_BYTES = 16
        private const val KEY_BITS = 256
        private const val KDF_ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val RECORD_PREFIX = "pbkdf2v1"
    }
}
