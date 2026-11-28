package com.diniy.duit.devcenter

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

/**
 * Verifies the locally installed APK's package ID, signing certificate SHA-256,
 * and private developer manifest metadata on-device without hardcoding credentials.
 */
object DevApkIntegrityChecker {
    const val EXPECTED_PACKAGE_ID = "com.diniy.duit.beta"
    const val EXPECTED_CERT_SHA256 = "e26f499ec2524650f5fbb7cf139e62f3de7dd001ad1afbbe1718034a2b785d14"

    @Suppress("DEPRECATION")
    fun inspectInstalled(context: Context): InstalledApkIntegrity {
        val ctx = context.applicationContext
        val pm = ctx.packageManager
        val flags = PackageManager.GET_META_DATA or PackageManager.GET_SIGNING_CERTIFICATES
        return runCatching {
            val info = pm.getPackageInfo(ctx.packageName, flags)
            val appInfo = pm.getApplicationInfo(ctx.packageName, PackageManager.GET_META_DATA)
            val isPrivateDevBuild = com.diniy.duit.BuildConfig.PRIVATE_DEV_EDITION
            val hasPrivateMeta = if (isPrivateDevBuild) {
                appInfo.metaData?.getBoolean("com.diniy.duit.PRIVATE_DEV_EDITION", false) == true
            } else {
                false
            }
            val signers = info.signingInfo?.apkContentsSigners.orEmpty()
            val sha256 = signers.firstOrNull()?.toByteArray()?.let { bytes ->
                MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            }.orEmpty()
            val certMatch = sha256.equals(EXPECTED_CERT_SHA256, ignoreCase = true)
            val vCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()
            val summary = when {
                ctx.packageName != EXPECTED_PACKAGE_ID -> "Non-beta package ID (${ctx.packageName})"
                !certMatch -> "Signing certificate mismatch!"
                isPrivateDevBuild -> if (hasPrivateMeta) {
                    "Verified original duit-beta certificate & private manifest."
                } else {
                    "Missing private metadata"
                }
                else -> "Verified original duit-beta certificate (Unified Public Edition)."
            }
            InstalledApkIntegrity(
                packageName = ctx.packageName,
                versionName = info.versionName.orEmpty(),
                versionCode = vCode,
                signerSha256 = sha256,
                matchesOriginalCert = certMatch,
                hasPrivateDevMeta = hasPrivateMeta,
                statusSummary = summary
            )
        }.getOrElse { err ->
            InstalledApkIntegrity(
                packageName = ctx.packageName,
                versionName = "unknown",
                versionCode = 0L,
                signerSha256 = "",
                matchesOriginalCert = false,
                hasPrivateDevMeta = false,
                statusSummary = err.message ?: "Unable to read installed package signature."
            )
        }
    }
}
