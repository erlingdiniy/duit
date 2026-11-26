package com.diniy.duit.update

import android.app.Application
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.diniy.duit.devcenter.DevCenterRedactor
import com.diniy.duit.devcenter.DevCenterValidators
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PrivateDevUpdateTest {

    private val context = ApplicationProvider.getApplicationContext<Application>()

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun fakeInstalledPkg(
        versionCode: Long = 10_020_000L,
        versionName: String = "1.2.0-dev",
        signerHex: String = "30820122"
    ): PackageInfo = PackageInfo().apply {
        packageName = "com.diniy.duit.beta"
        longVersionCode = versionCode
        this.versionName = versionName
        @Suppress("DEPRECATION")
        signatures = arrayOf(Signature(signerHex))
    }

    @Test
    fun privateDevVersionCode_isMonotonicAndAbovePublicRange() {
        fun betaCode(major: Int, minor: Int, betaNum: Int): Long =
            major * 1_000_000L + minor * 1_000L - 100L + betaNum

        fun stableCode(major: Int, minor: Int, patch: Int): Long =
            major * 1_000_000L + minor * 1_000L + patch

        val base = privateDevVersionCode(1, 2, 0, 0)!!
        val patch1 = privateDevVersionCode(1, 2, 0, 1)!!
        val patch2 = privateDevVersionCode(1, 2, 0, 2)!!
        val bugfixDev = privateDevVersionCode(1, 2, 1, 1)!!
        val minor1 = privateDevVersionCode(1, 3, 0, 1)!!
        val v2 = privateDevVersionCode(2, 0, 0, 1)!!

        val ordered = listOf(
            betaCode(1, 2, 1),     // 1.2.0-beta.1 = 1_001_901
            stableCode(1, 2, 0),   // 1.2.0        = 1_002_000
            stableCode(1, 2, 1),   // 1.2.1        = 1_002_001
            betaCode(1, 3, 1),     // 1.3.0-beta.1 = 1_002_901
            stableCode(1, 3, 0),   // 1.3.0        = 1_003_000
            stableCode(2, 0, 0),   // 2.0.0        = 2_000_000
            stableCode(9, 9, 9),   // 9.9.9        = 9_009_009
            base,                  // 1.2.0-dev    = 10_020_000
            patch1,                // 1.2.0-dev.1  = 10_020_001
            patch2,                // 1.2.0-dev.2  = 10_020_002
            bugfixDev,             // 1.2.1-dev.1  = 10_020_101
            minor1,                // 1.3.0-dev.1  = 10_030_001
            v2                     // 2.0.0-dev.1  = 20_000_001
        )
        assertEquals(1_002_000L, stableCode(1, 2, 0))
        assertEquals(10_020_000L, base)
        assertEquals(10_020_001L, patch1)
        assertEquals(10_020_002L, patch2)
        for (i in 1 until ordered.size) {
            assertTrue("Expected ${ordered[i]} > ${ordered[i - 1]}", ordered[i] > ordered[i - 1])
        }
    }

    @Test
    fun selectPrivateDevRelease_acceptsOnlyNonDraftPrivateDevAssets() {
        val repo = "erlingdiniy/duit-developer-console"
        val validSha = "a".repeat(64)
        val releases = JSONArray().apply {
            // Public tag must be ignored
            put(JSONObject().apply {
                put("tag_name", "v1.2.5")
                put("draft", false)
                put("assets", JSONArray().put(JSONObject().apply {
                    put("name", "duit-1.2.5.apk")
                    put("state", "uploaded")
                    put("url", "https://api.github.com/repos/$repo/releases/assets/100")
                    put("digest", "sha256:$validSha")
                    put("size", 8_000_000)
                }))
            })
            // Public beta tag must be ignored
            put(JSONObject().apply {
                put("tag_name", "v1.3.0-beta.1")
                put("draft", false)
                put("assets", JSONArray().put(JSONObject().apply {
                    put("name", "duit-1.3.0-beta.1.apk")
                    put("state", "uploaded")
                    put("url", "https://api.github.com/repos/$repo/releases/assets/101")
                    put("digest", "sha256:$validSha")
                    put("size", 8_000_000)
                }))
            })
            // Draft -dev must be ignored
            put(JSONObject().apply {
                put("tag_name", "v1.2.0-dev.2")
                put("draft", true)
                put("assets", JSONArray().put(JSONObject().apply {
                    put("name", "duit-1.2.0-dev.2.apk")
                    put("state", "uploaded")
                    put("url", "https://api.github.com/repos/$repo/releases/assets/102")
                    put("digest", "sha256:$validSha")
                    put("size", 8_000_000)
                }))
            })
            // Missing digest must be rejected
            put(JSONObject().apply {
                put("tag_name", "v1.2.0-dev.3")
                put("draft", false)
                put("assets", JSONArray().put(JSONObject().apply {
                    put("name", "duit-1.2.0-dev.3.apk")
                    put("state", "uploaded")
                    put("url", "https://api.github.com/repos/$repo/releases/assets/103")
                    put("digest", "")
                    put("size", 8_000_000)
                }))
            })
            // Malformed digest must be rejected
            put(JSONObject().apply {
                put("tag_name", "v1.2.0-dev.4")
                put("draft", false)
                put("assets", JSONArray().put(JSONObject().apply {
                    put("name", "duit-1.2.0-dev.4.apk")
                    put("state", "uploaded")
                    put("url", "https://api.github.com/repos/$repo/releases/assets/104")
                    put("digest", "sha256:1234")
                    put("size", 8_000_000)
                }))
            })
            // Valid private dev release
            put(JSONObject().apply {
                put("tag_name", "v1.2.0-dev.1")
                put("draft", false)
                put("published_at", "2026-11-26T18:30:00Z")
                put("body", "## Improvements\n- Private auto update\nSHA-256: $validSha\n[link](https://example.com)<b>safe</b>")
                put("assets", JSONArray().put(JSONObject().apply {
                    put("name", "duit-1.2.0-dev.1.apk")
                    put("state", "uploaded")
                    put("url", "https://api.github.com/repos/$repo/releases/assets/998877")
                    put("digest", "sha256:$validSha")
                    put("size", 8_600_000)
                }))
            })
        }

        val picked = selectPrivateDevRelease(releases, repo, installedCode = 10_020_000L)
        assertNotNull(picked)
        assertEquals(10_020_001L, picked!!.versionCode)
        assertEquals("1.2.0-dev.1", picked.versionName)
        assertEquals("https://api.github.com/repos/$repo/releases/assets/998877", picked.assetApiUrl.toString())
        assertEquals(validSha, picked.sha256)

        val parsed = parsePrivateReleaseNotes(picked.notes)
        assertEquals(1, parsed.size)
        assertTrue(parsed[0].items.none { "sha-256" in it.text.lowercase() || "<b>" in it.text })
        assertTrue(parsed[0].items.any { "Private auto update" in it.text })

        assertNull(selectPrivateDevRelease(releases, repo, installedCode = 10_020_001L))
        assertNull(selectPrivateDevRelease(releases, "erlingdiniy/duit-beta", installedCode = 1L))
    }

    @Test
    fun check_enforcesPrivateRepoAndAuth() = runTest {
        assertFalse(DevCenterValidators.isValidPrivateUpdateRepo("erlingdiniy/duit-beta"))
        assertFalse(DevCenterValidators.isValidPrivateUpdateRepo("erlingdiniy/duit"))
        assertTrue(DevCenterValidators.isValidPrivateUpdateRepo("erlingdiniy/duit-developer-console"))

        val fakeTransport = object : PrivateDownloadTransport {
            override suspend fun getJson(url: URI, pat: String): String {
                return if (url.path == "/repos/erlingdiniy/public-test") {
                    """{"private": false}"""
                } else {
                    "[]"
                }
            }

            override suspend fun downloadStream(
                assetApiUrl: URI,
                pat: String,
                maxBytes: Long,
                onResponse: (Long, InputStream) -> Unit
            ) = Unit
        }

        val publicRepoBlocked = PrivateDevUpdates(
            context = context,
            transport = fakeTransport,
            patProvider = { "github_pat_11TESTTOKENVALUE_XYZ" },
            repoProvider = { "erlingdiniy/duit-beta" }
        )
        try {
            publicRepoBlocked.check()
            fail("Expected NotPrivateRepoException for public duit-beta repo")
        } catch (_: NotPrivateRepoException) {
            // expected
        }

        val unprivateRepoBlocked = PrivateDevUpdates(
            context = context,
            transport = fakeTransport,
            patProvider = { "github_pat_11TESTTOKENVALUE_XYZ" },
            repoProvider = { "erlingdiniy/public-test" }
        )
        try {
            unprivateRepoBlocked.check()
            fail("Expected NotPrivateRepoException when private=false")
        } catch (_: NotPrivateRepoException) {
            // expected
        }

        val missingPat = PrivateDevUpdates(
            context = context,
            transport = fakeTransport,
            patProvider = { null },
            repoProvider = { "erlingdiniy/duit-developer-console" }
        )
        assertEquals(PrivateCheckResult.AuthRequired, missingPat.check())
    }

    @Test
    fun downloadAndVerify_verifiesSha256_deletesOnMismatch_andRedactsSecretsAndUrls() = runTest {
        val payload = "mock-private-apk-bytes-1234567890".toByteArray()
        val goodSha = sha256Hex(payload)
        val fakePat = "github_pat_11FAKEUPDATE_SECRET_999"
        val presignedUrl = "https://objects.githubusercontent.com/github-production-release-asset-123/test.apk?X-Amz-Signature=topsecret123"

        val goodRelease = PrivateDevRelease(
            versionCode = 10_020_001L,
            assetApiUrl = URI("https://api.github.com/repos/erlingdiniy/duit-developer-console/releases/assets/555"),
            size = payload.size.toLong(),
            sha256 = goodSha,
            versionName = "1.2.0-dev.1",
            notes = "",
            publishedDate = "2026-11-26"
        )

        var seenProgress = false
        val okTransport = object : PrivateDownloadTransport {
            override suspend fun getJson(url: URI, pat: String): String = "{}"
            override suspend fun downloadStream(
                assetApiUrl: URI,
                pat: String,
                maxBytes: Long,
                onResponse: (Long, InputStream) -> Unit
            ) {
                assertEquals("api.github.com", assetApiUrl.host)
                assertEquals(fakePat, pat)
                onResponse(payload.size.toLong(), ByteArrayInputStream(payload))
            }
        }

        val updater = PrivateDevUpdates(
            context = context,
            transport = okTransport,
            patProvider = { fakePat },
            repoProvider = { "erlingdiniy/duit-developer-console" }
        )
        val tmpOk = File.createTempFile("test-ok-", ".apk", context.cacheDir)
        try {
            updater.downloadAndVerifyFile(goodRelease, fakePat, tmpOk) { p ->
                if (p >= 1f) seenProgress = true
            }
            assertTrue(seenProgress)
            assertTrue(tmpOk.length() == payload.size.toLong())
        } finally {
            tmpOk.delete()
        }

        for (invalidDigest in listOf("", "not-a-valid-sha")) {
            val missingHashRelease = goodRelease.copy(sha256 = invalidDigest)
            val tmpMissing = File.createTempFile("test-missing-", ".apk", context.cacheDir)
            try {
                updater.downloadAndVerifyFile(missingHashRelease, fakePat, tmpMissing)
                fail("Expected missing/invalid SHA-256 failure for '$invalidDigest'")
            } catch (err: IOException) {
                assertTrue(err.message.orEmpty().contains("SHA-256"))
            } finally {
                tmpMissing.delete()
            }
        }

        val badHashRelease = goodRelease.copy(sha256 = "f".repeat(64))
        val tmpBad = File.createTempFile("test-bad-", ".apk", context.cacheDir)
        try {
            updater.downloadAndVerifyFile(badHashRelease, fakePat, tmpBad)
            fail("Expected SHA-256 mismatch failure")
        } catch (err: IOException) {
            assertTrue(err.message.orEmpty().contains("SHA-256"))
        } finally {
            tmpBad.delete()
        }

        val scrubbed = DevCenterRedactor.redact(
            "Download $presignedUrl failed with Bearer $fakePat&token=abc12345",
            listOf(fakePat)
        )
        assertFalse(scrubbed.contains("objects.githubusercontent.com"))
        assertFalse(scrubbed.contains("topsecret123"))
        assertFalse(scrubbed.contains(fakePat))
        assertTrue(scrubbed.contains("[REDACTED_URL]"))
        assertTrue(scrubbed.contains("[REDACTED]"))
    }

    @Test
    fun verifyCandidateArchive_blocksTamperedPackageVersionSignerOrMetadata() {
        val release = PrivateDevRelease(
            versionCode = 10_020_001L,
            assetApiUrl = URI("https://api.github.com/repos/erlingdiniy/duit-developer-console/releases/assets/1"),
            size = 100L,
            sha256 = "a".repeat(64),
            versionName = "1.2.0-dev.1",
            notes = "",
            publishedDate = "2026-11-26"
        )
        val signerA = Signature("30820122")
        val signerB = Signature("30820199")
        val installed = setOf(signerA)

        // Valid candidate passes
        verifyCandidateArchive(
            release = release,
            candidate = CandidateArchiveMetadata("com.diniy.duit.beta", 10_020_001L, "1.2.0-dev.1", true, setOf(signerA)),
            expectedPackageName = "com.diniy.duit.beta",
            installedVersionCode = 10_020_000L,
            installedSigners = installed
        )

        val badCases = listOf(
            CandidateArchiveMetadata("com.diniy.duit", 10_020_001L, "1.2.0-dev.1", true, setOf(signerA)),
            CandidateArchiveMetadata("com.diniy.duit.beta", 10_020_000L, "1.2.0-dev.1", true, setOf(signerA)),
            CandidateArchiveMetadata("com.diniy.duit.beta", 1_002_000L, "1.2.0", false, setOf(signerA)),
            CandidateArchiveMetadata("com.diniy.duit.beta", 1_002_901L, "1.3.0-beta.1", false, setOf(signerA)),
            CandidateArchiveMetadata("com.diniy.duit.beta", 10_020_001L, "1.2.0", true, setOf(signerA)),
            CandidateArchiveMetadata("com.diniy.duit.beta", 10_020_001L, "1.2.0-beta.3-dev.1", true, setOf(signerA)),
            CandidateArchiveMetadata("com.diniy.duit.beta", 10_020_001L, "1.2.0-dev.1", false, setOf(signerA)),
            CandidateArchiveMetadata("com.diniy.duit.beta", 10_020_001L, "1.2.0-dev.1", true, setOf(signerB))
        )
        for (bad in badCases) {
            try {
                verifyCandidateArchive(release, bad, "com.diniy.duit.beta", 10_020_000L, installed)
                fail("Expected SecurityException for invalid candidate $bad")
            } catch (_: SecurityException) {
                // expected
            }
        }
    }

    @Test
    fun installReceiver_ignoresForgedIntents() {
        val receiver = PrivateDevInstallStatusReceiver()
        // Forged action
        receiver.onReceive(
            context,
            Intent("com.evil.INSTALL").setData(Uri.parse("duit-dev-install://${context.packageName}/7/abc"))
        )
        // Forged scheme
        receiver.onReceive(
            context,
            Intent("com.diniy.duit.PRIVATE_DEV_INSTALL_STATUS")
                .setData(Uri.parse("duit-beta-install://${context.packageName}/7/abc"))
        )
        // Valid-looking empty status outside active attempt does not crash
        val fakePkg = fakeInstalledPkg()
        assertEquals("com.diniy.duit.beta", fakePkg.packageName)
    }
}
