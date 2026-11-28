package com.diniy.duit.devcenter

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.util.Base64
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec

data class DeviceKeyMetadata(
    val alias: String,
    val publicKeySpkiBase64: String,
    val hardwareBacked: Boolean,
    val attestationSummary: String
)

interface DeviceSigningKeyManager {
    fun hasKey(): Boolean
    fun ensureKeyPair(): DeviceKeyMetadata
    fun signChallengePayload(payload: String): String
    fun deleteKey()
}

/**
 * Hardware-backed AndroidKeyStore ECDSA P-256 device identity key manager.
 * Private keys never leave hardware TEE/StrongBox and prove device possession to Cloudflare Worker.
 */
class AndroidKeyStoreDeviceKeyManager(
    private val keyAlias: String = DEFAULT_KEY_ALIAS
) : DeviceSigningKeyManager {

    override fun hasKey(): Boolean = runCatching {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        ks.containsAlias(keyAlias)
    }.getOrDefault(false)

    override fun ensureKeyPair(): DeviceKeyMetadata {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (!ks.containsAlias(keyAlias)) {
            generateKeyPair(preferStrongBox = true)
        }
        val entry = ks.getEntry(keyAlias, null) as? KeyStore.PrivateKeyEntry
            ?: error("AndroidKeyStore key entry missing for $keyAlias")
        val spkiBase64 = Base64.encodeToString(entry.certificate.publicKey.encoded, Base64.NO_WRAP)
        val (hwBacked, summary) = inspectSecurityLevel(entry.privateKey)
        return DeviceKeyMetadata(
            alias = keyAlias,
            publicKeySpkiBase64 = spkiBase64,
            hardwareBacked = hwBacked,
            attestationSummary = summary
        )
    }

    override fun signChallengePayload(payload: String): String {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val entry = ks.getEntry(keyAlias, null) as? KeyStore.PrivateKeyEntry
            ?: error("Hardware device key not initialized")
        val sig = Signature.getInstance(SIGN_ALGORITHM).apply {
            initSign(entry.privateKey)
            update(payload.toByteArray(Charsets.UTF_8))
        }.sign()
        return Base64.encodeToString(sig, Base64.NO_WRAP)
    }

    override fun deleteKey() {
        runCatching {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (ks.containsAlias(keyAlias)) {
                ks.deleteEntry(keyAlias)
            }
        }
    }

    private fun generateKeyPair(preferStrongBox: Boolean) {
        val kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE)
        val builder = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setUserAuthenticationRequired(false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && preferStrongBox) {
            builder.setIsStrongBoxBacked(true)
        }

        try {
            kpg.initialize(builder.build())
            kpg.generateKeyPair()
        } catch (err: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                preferStrongBox &&
                (err is StrongBoxUnavailableException || err.cause is StrongBoxUnavailableException)
            ) {
                generateKeyPair(preferStrongBox = false)
            } else {
                throw err
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun inspectSecurityLevel(privateKey: PrivateKey): Pair<Boolean, String> = runCatching {
        val factory = KeyFactory.getInstance(privateKey.algorithm, ANDROID_KEYSTORE)
        val info = factory.getKeySpec(privateKey, KeyInfo::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val level = info.securityLevel
            when (level) {
                KeyProperties.SECURITY_LEVEL_STRONGBOX -> true to "StrongBox ECDSA P-256 (API ${Build.VERSION.SDK_INT})"
                KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT -> true to "Hardware TEE ECDSA P-256 (API ${Build.VERSION.SDK_INT})"
                else -> info.isInsideSecureHardware to "Software KeyStore ECDSA P-256 (API ${Build.VERSION.SDK_INT})"
            }
        } else {
            val hw = info.isInsideSecureHardware
            hw to if (hw) {
                "Hardware TEE ECDSA P-256 (API ${Build.VERSION.SDK_INT})"
            } else {
                "Software KeyStore ECDSA P-256 (API ${Build.VERSION.SDK_INT})"
            }
        }
    }.getOrDefault(false to "AndroidKeyStore ECDSA P-256")

    companion object {
        const val DEFAULT_KEY_ALIAS = "duit_dev_mc_ecdsa_p256_v1"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val SIGN_ALGORITHM = "SHA256withECDSA"
    }
}
