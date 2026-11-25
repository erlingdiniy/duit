package com.diniy.duit.update

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = android.app.Application::class)
class StableChannelTest {
    private val repo = "erlingdiniy/duit-beta"

    private fun release(tag: String, asset: String, prerelease: Boolean, body: String = "") = JSONObject()
        .put("tag_name", tag).put("draft", false).put("prerelease", prerelease).put("name", "duit. $tag").put("body", body)
        .put("assets", JSONArray().put(JSONObject()
            .put("name", asset).put("state", "uploaded").put("size", 7_400_000)
            .put("browser_download_url", "https://github.com/$repo/releases/download/$tag/$asset")
            .put("digest", "sha256:" + "c".repeat(64))))

    private val feed = JSONArray()
        .put(release("v1.0.0", "duit-1.0.0.apk", prerelease = false, body = "stable notes"))
        .put(release("beta-5", "duit-beta-5.apk", prerelease = true, body = "bridge notes"))
        .put(release("beta-4", "duit-beta-4.apk", prerelease = true))

    @Test
    fun stableVersionCode_isAboveEveryBeta() {
        assertEquals(1_000_000L, stableVersionCode(1, 0, 0))
        assertEquals(1_002_003L, stableVersionCode(1, 2, 3))
    }

    @Test
    fun bridgeBeta_findsStable_withItsExactAsset() {
        val found = selectBetaRelease(feed, repo, installedCode = 5, includePrereleases = true)!!
        assertEquals(1_000_000L, found.versionCode)
        assertEquals("https://github.com/$repo/releases/download/v1.0.0/duit-1.0.0.apk", found.download.toString())
        assertEquals("1.0.0", found.versionName)
    }

    @Test
    fun olderBeta_seesTheHighestRelease_andStableIgnoresBetas() {
        assertEquals(1_000_000L, selectBetaRelease(feed, repo, installedCode = 4)!!.versionCode)
        assertNull(selectBetaRelease(feed, repo, installedCode = 1_000_000L, includePrereleases = false))
        val laterBeta = JSONArray().put(release("beta-1000001", "duit-beta-1000001.apk", prerelease = true))
        assertNull(selectBetaRelease(laterBeta, repo, installedCode = 1_000_000L, includePrereleases = false))
    }

    @Test
    fun malformedStableReleases_areRejected() {
        val bad = JSONArray()
            .put(release("v1.0", "duit-1.0.apk", prerelease = false))
            .put(release("v1.0.0", "duit-beta-1000000.apk", prerelease = false))
            .put(release("1.0.0", "duit-1.0.0.apk", prerelease = false))
        assertNull(selectBetaRelease(bad, repo, installedCode = 5))
    }

    @Test
    fun welcomeNotes_matchStableByVersionCode() {
        assertEquals("stable notes", findReleaseNotes(feed, 1_000_000L))
        assertEquals("bridge notes", findReleaseNotes(feed, 5))
    }

    @Test
    fun stable120_excludesFutureBetas_andAcceptsNewerStable() {
        assertEquals(1_002_000L, stableVersionCode(1, 2, 0))

        val futureBetaFeed = JSONArray()
            .put(release("v1.3.0-beta.1", "duit-1.3.0-beta.1.apk", prerelease = true))
        assertNull(selectBetaRelease(futureBetaFeed, repo, installedCode = 1_002_000L, includePrereleases = false))

        val mixedFeed = JSONArray()
            .put(release("v1.3.0-beta.1", "duit-1.3.0-beta.1.apk", prerelease = true))
            .put(release("v1.2.1", "duit-1.2.1.apk", prerelease = false, body = "patch notes"))
        val found = selectBetaRelease(mixedFeed, repo, installedCode = 1_002_000L, includePrereleases = false)!!
        assertEquals(1_002_001L, found.versionCode)
        assertEquals("1.2.1", found.versionName)
    }
}
