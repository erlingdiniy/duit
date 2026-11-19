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
class BetaReleaseTest {
    @Test
    fun selectsHighestEligibleVersionIndependentOfReleaseOrder() {
        val releases = JSONArray().put(release(4)).put(release(8)).put(release(6))
        assertEquals(8L, selectBetaRelease(releases, REPO, 3)?.versionCode)
        assertNull(selectBetaRelease(releases, REPO, 8))
    }

    @Test
    fun rejectsUntrustedIncompleteAndNonBetaReleases() {
        val releases = JSONArray()
            .put(release(9, url = "https://example.com/duit-beta-9.apk"))
            .put(release(10, url = "http://github.com/$REPO/releases/download/beta-10/duit-beta-10.apk"))
            .put(release(11, digest = ""))
            .put(release(12, size = 200_000_000))
            .put(release(13).put("draft", true))
            .put(release(14).put("prerelease", false))
        assertNull(selectBetaRelease(releases, REPO, 1))
    }

    private fun release(
        code: Int,
        url: String = "https://github.com/$REPO/releases/download/beta-$code/duit-beta-$code.apk",
        digest: String = "sha256:" + "a".repeat(64),
        size: Long = 100
    ): JSONObject {
        val asset = JSONObject()
            .put("name", "duit-beta-$code.apk")
            .put("state", "uploaded")
            .put("browser_download_url", url)
            .put("digest", digest)
            .put("size", size)
        return JSONObject()
            .put("draft", false)
            .put("prerelease", true)
            .put("tag_name", "beta-$code")
            .put("assets", JSONArray().put(asset))
    }

    private companion object {
        const val REPO = "example/duit-beta"
    }
}
