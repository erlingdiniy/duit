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
class ClosedBetaChannelTest {
    private val repo = "erlingdiniy/duit-beta"

    private fun release(tag: String, prerelease: Boolean, asset: String = if (tag.startsWith("v")) "duit-${tag.drop(1)}.apk" else "duit-$tag.apk") =
        JSONObject().put("tag_name", tag).put("draft", false).put("prerelease", prerelease).put("name", "duit. ${tag.drop(1)}")
            .put("assets", JSONArray().put(JSONObject()
                .put("name", asset).put("state", "uploaded").put("size", 7_700_000)
                .put("browser_download_url", "https://github.com/$repo/releases/download/$tag/$asset")
                .put("digest", "sha256:" + "d".repeat(64))))

    // The live feed today plus the first closed beta.
    private val feed = JSONArray()
        .put(release("v1.2.0-beta.1", prerelease = true))
        .put(release("v1.1.1", prerelease = false))
        .put(release("v1.1.0", prerelease = false))
        .put(release("beta-5", prerelease = true))

    @Test
    fun betaCodes_sitBetweenTheLastStableAndTheNextOne() {
        assertEquals(1_001_901L, closedBetaVersionCode(1, 2, 0, 1))
        assertEquals(1_001_999L, closedBetaVersionCode(1, 2, 0, 99))
        assertEquals(1_999_901L, closedBetaVersionCode(2, 0, 0, 1))
        assert(closedBetaVersionCode(1, 2, 0, 1)!! > stableVersionCode(1, 1, 1))
        assert(closedBetaVersionCode(1, 2, 0, 99)!! < stableVersionCode(1, 2, 0))
        // Patch betas, N outside 1..99 and 0.0.0 would collide or go below zero, so they are rejected.
        assertNull(closedBetaVersionCode(1, 2, 3, 1))
        assertNull(closedBetaVersionCode(1, 2, 0, 0))
        assertNull(closedBetaVersionCode(1, 2, 0, 100))
        assertNull(closedBetaVersionCode(0, 0, 0, 1))
    }

    @Test
    fun stableBuild_ignoresClosedBetas() {
        assertNull(selectBetaRelease(feed, repo, installedCode = 1_001_001L, includePrereleases = false))
    }

    @Test
    fun stableUserOptingIntoBeta_isOfferedTheBeta_withItsExactAsset() {
        val found = selectBetaRelease(feed, repo, installedCode = 1_001_001L, includePrereleases = true)!!
        assertEquals(1_001_901L, found.versionCode)
        assertEquals("https://github.com/$repo/releases/download/v1.2.0-beta.1/duit-1.2.0-beta.1.apk", found.download.toString())
        assertEquals("1.2.0-beta.1", found.versionName)
    }

    @Test
    fun betaBuild_followsNewerBetas_thenTheStableRelease() {
        val later = JSONArray(feed.toString())
            .put(release("v1.2.0-beta.2", prerelease = true))
            .put(release("v1.2.0", prerelease = false))
        assertEquals(1_002_000L, selectBetaRelease(later, repo, installedCode = 1_001_901L)!!.versionCode)
        val betasOnly = JSONArray(feed.toString()).put(release("v1.2.0-beta.2", prerelease = true))
        assertEquals(1_001_902L, selectBetaRelease(betasOnly, repo, installedCode = 1_001_901L)!!.versionCode)
    }

    @Test
    fun neverOffersADowngrade_orTheSameBuild() {
        // Installed beta.2: beta.1, 1.1.1 and legacy beta-5 are all older.
        assertNull(selectBetaRelease(feed, repo, installedCode = 1_001_902L))
        assertNull(selectBetaRelease(feed, repo, installedCode = 1_001_901L))
    }

    @Test
    fun malformedBetaReleases_areRejected() {
        val bad = JSONArray()
            .put(release("v1.2.3-beta.1", prerelease = true)) // patch beta
            .put(release("v1.3.0-beta.1", prerelease = false)) // beta tag published as stable
            .put(release("v1.4.0-beta.1", prerelease = true, asset = "duit-beta-1.apk")) // wrong asset name
            .put(release("v1.5.0-beta.01", prerelease = true)) // leading zero
        assertNull(selectBetaRelease(bad, repo, installedCode = 1_001_001L, includePrereleases = true))
    }
}
