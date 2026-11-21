package com.diniy.duit.backup

import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

sealed class BackupException(message: String, cause: Throwable? = null) : Exception(message, cause)
class BackupFormatException(message: String, cause: Throwable? = null) : BackupException(message, cause)
class BackupVersionException(message: String) : BackupException(message)
class BackupAuthOrIntegrityException(message: String, cause: Throwable? = null) : BackupException(message, cause)

data class BackupEnvelopeHeader(
    val formatVersion: Int,
    val dbSchemaVersion: Int,
    val createdAtEpochMs: Long,
    val kdf: String,
    val iterations: Int,
    val salt: ByteArray,
    val iv: ByteArray,
    val ciphertext: ByteArray
)

/**
 * Portable password-authenticated local backup encryption engine:
 * - Key derivation: PBKDF2WithHmacSHA256, 150,000 iterations, 256-bit key, random 128-bit salt per backup.
 * - Encryption: AES-256-GCM (128-bit authentication tag) with fresh random 96-bit nonce per backup.
 * - Integrity: GCM AAD binds header metadata + inner SHA-256 digest of the UTF-8 payload JSON.
 * - Never relies on Android Keystore so encrypted files restore cleanly across reinstalls and devices.
 * - Never embeds keys or logs passwords/payloads.
 */
object BackupCryptoEngine {

    const val MAGIC = "DUIT_ENCRYPTED_BACKUP"
    const val CURRENT_FORMAT_VERSION = 1
    // v10 adds money reminders. Older (v1-9) backups still restore; older apps refuse v10 backups instead of dropping reminders.
    const val CURRENT_DB_SCHEMA_VERSION = 10
    const val KDF_ALGORITHM = "PBKDF2WithHmacSHA256"
    const val CIPHER_ALGORITHM = "AES/GCM/NoPadding"
    const val DEFAULT_ITERATIONS = 150_000
    const val MIN_PASSWORD_LENGTH = 6

    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val KEY_BITS = 256
    private const val GCM_TAG_BITS = 128
    private const val MAX_ALLOWED_ITERATIONS = 1_000_000
    private const val MIN_ALLOWED_ITERATIONS = 10_000

    fun encrypt(
        plaintextJson: String,
        password: CharArray,
        dbSchemaVersion: Int = CURRENT_DB_SCHEMA_VERSION,
        createdAtEpochMs: Long = System.currentTimeMillis(),
        iterations: Int = DEFAULT_ITERATIONS,
        random: SecureRandom = SecureRandom()
    ): ByteArray {
        require(password.size >= MIN_PASSWORD_LENGTH) {
            "Backup password must be at least $MIN_PASSWORD_LENGTH characters."
        }
        val salt = ByteArray(SALT_BYTES).also { random.nextBytes(it) }
        val iv = ByteArray(IV_BYTES).also { random.nextBytes(it) }

        val innerWrapper = JSONObject().apply {
            put("sha256", sha256Hex(plaintextJson.toByteArray(StandardCharsets.UTF_8)))
            put("payload", JSONObject(plaintextJson))
        }.toString().toByteArray(StandardCharsets.UTF_8)

        val keyBytes = deriveKey(password, salt, iterations)
        val ciphertext = try {
            val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
            val keySpec = SecretKeySpec(keyBytes, "AES")
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.updateAAD(buildAad(CURRENT_FORMAT_VERSION, dbSchemaVersion, KDF_ALGORITHM, iterations))
            cipher.doFinal(innerWrapper)
        } finally {
            keyBytes.fill(0)
            innerWrapper.fill(0)
        }

        val b64 = Base64.getEncoder()
        val envelope = JSONObject().apply {
            put("magic", MAGIC)
            put("formatVersion", CURRENT_FORMAT_VERSION)
            put("dbSchemaVersion", dbSchemaVersion)
            put("createdAtEpochMs", createdAtEpochMs)
            put("kdf", KDF_ALGORITHM)
            put("iterations", iterations)
            put("cipher", CIPHER_ALGORITHM)
            put("saltB64", b64.encodeToString(salt))
            put("ivB64", b64.encodeToString(iv))
            put("ciphertextB64", b64.encodeToString(ciphertext))
        }
        return envelope.toString().toByteArray(StandardCharsets.UTF_8)
    }

    fun inspectEnvelope(rawBytes: ByteArray): BackupEnvelopeHeader {
        if (rawBytes.isEmpty()) throw BackupFormatException("Selected backup file is empty.")
        val json = runCatching {
            JSONObject(String(rawBytes, StandardCharsets.UTF_8))
        }.getOrElse {
            throw BackupFormatException("File is not a valid duit. backup.", it)
        }

        val magic = json.optString("magic", "")
        if (magic != MAGIC) {
            throw BackupFormatException("Unrecognized backup file header.")
        }

        val formatVersion = json.optInt("formatVersion", -1)
        if (formatVersion !in 1..CURRENT_FORMAT_VERSION) {
            throw BackupVersionException("Unsupported backup format version: $formatVersion.")
        }

        val dbSchemaVersion = json.optInt("dbSchemaVersion", -1)
        if (dbSchemaVersion !in 1..CURRENT_DB_SCHEMA_VERSION) {
            throw BackupVersionException("Incompatible backup database schema version: $dbSchemaVersion.")
        }

        val kdf = json.optString("kdf", "")
        if (kdf != KDF_ALGORITHM) {
            throw BackupFormatException("Unsupported key derivation algorithm.")
        }

        val cipher = json.optString("cipher", "")
        if (cipher != CIPHER_ALGORITHM) {
            throw BackupFormatException("Unsupported backup cipher.")
        }

        val iterations = json.optInt("iterations", -1)
        if (iterations !in MIN_ALLOWED_ITERATIONS..MAX_ALLOWED_ITERATIONS) {
            throw BackupFormatException("Invalid backup key derivation parameters.")
        }

        val createdAt = json.optLong("createdAtEpochMs", 0L)
        val decoder = Base64.getDecoder()
        val salt = runCatching { decoder.decode(json.getString("saltB64")) }
            .getOrNull()?.takeIf { it.size == SALT_BYTES }
            ?: throw BackupFormatException("Backup salt is missing or malformed.")
        val iv = runCatching { decoder.decode(json.getString("ivB64")) }
            .getOrNull()?.takeIf { it.size == IV_BYTES }
            ?: throw BackupFormatException("Backup nonce is missing or malformed.")
        val ciphertext = runCatching { decoder.decode(json.getString("ciphertextB64")) }
            .getOrNull()?.takeIf { it.size > 16 }
            ?: throw BackupFormatException("Backup ciphertext is missing or truncated.")

        return BackupEnvelopeHeader(
            formatVersion = formatVersion,
            dbSchemaVersion = dbSchemaVersion,
            createdAtEpochMs = createdAt,
            kdf = kdf,
            iterations = iterations,
            salt = salt,
            iv = iv,
            ciphertext = ciphertext
        )
    }

    fun decrypt(rawBytes: ByteArray, password: CharArray): String {
        if (password.isEmpty()) throw BackupAuthOrIntegrityException("Please enter the backup password.")
        val header = inspectEnvelope(rawBytes)
        val keyBytes = deriveKey(password, header.salt, header.iterations)
        val decryptedBytes = try {
            val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
            val keySpec = SecretKeySpec(keyBytes, "AES")
            cipher.init(Cipher.DECRYPT_MODE, keySpec, GCMParameterSpec(GCM_TAG_BITS, header.iv))
            cipher.updateAAD(
                buildAad(
                    formatVersion = header.formatVersion,
                    dbSchemaVersion = header.dbSchemaVersion,
                    kdf = header.kdf,
                    iterations = header.iterations
                )
            )
            cipher.doFinal(header.ciphertext)
        } catch (err: GeneralSecurityException) {
            throw BackupAuthOrIntegrityException(
                "Incorrect backup password or corrupted backup file.",
                err
            )
        } finally {
            keyBytes.fill(0)
        }

        val wrapperText = try {
            String(decryptedBytes, StandardCharsets.UTF_8)
        } finally {
            decryptedBytes.fill(0)
        }

        val wrapperJson = runCatching { JSONObject(wrapperText) }.getOrElse {
            throw BackupAuthOrIntegrityException("Decrypted backup payload is corrupted.", it)
        }
        val expectedSha = wrapperJson.optString("sha256", "")
        val payloadObj = wrapperJson.optJSONObject("payload")
            ?: throw BackupAuthOrIntegrityException("Backup payload content is missing.")
        val payloadStr = payloadObj.toString()
        val actualSha = sha256Hex(payloadStr.toByteArray(StandardCharsets.UTF_8))
        if (!MessageDigest.isEqual(
                expectedSha.toByteArray(StandardCharsets.UTF_8),
                actualSha.toByteArray(StandardCharsets.UTF_8)
            )
        ) {
            throw BackupAuthOrIntegrityException("Backup integrity checksum mismatch.")
        }
        return payloadStr
    }

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, KEY_BITS)
        return try {
            val factory = SecretKeyFactory.getInstance(KDF_ALGORITHM)
            factory.generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun buildAad(
        formatVersion: Int,
        dbSchemaVersion: Int,
        kdf: String,
        iterations: Int
    ): ByteArray = "$MAGIC|$formatVersion|$dbSchemaVersion|$kdf|$iterations"
        .toByteArray(StandardCharsets.UTF_8)

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
