package com.diniy.duit.update

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class UnifiedMigrationVersionTest {
    private val repo = "erlingdiniy/duit-beta"
    private val validDigest = "sha256:" + "d".repeat(64)

    @Test
    fun unified1100_upgradesInPlaceFromBothPublicV130_andPrivateV140Dev1() {
        val v11Code = stableVersionCode(11, 0, 0)
        assertEquals(11_000_000L, v11Code)
        // Must strictly exceed both public stable v1.3.0 (1_003_000) and private v1.4.0-dev.1 (10_040_001)
        assertTrue(v11Code > 1_003_000L)
        assertTrue(v11Code > 10_040_001L)

        val feed = JSONArray().put(
            JSONObject()
                .put("tag_name", "v11.0.0")
                .put("draft", false)
                .put("prerelease", false)
                .put("name", "duit. v11.0.0")
                .put("body", "- Unified edition release")
                .put(
                    "assets",
                    JSONArray().put(
                        JSONObject()
                            .put("name", "duit-11.0.0.apk")
                            .put("state", "uploaded")
                            .put("size", 8_900_000)
                            .put("browser_download_url", "https://github.com/$repo/releases/download/v11.0.0/duit-11.0.0.apk")
                            .put("digest", validDigest)
                    )
                )
        )

        val fromPublic130 = selectBetaRelease(feed, repo, installedCode = 1_003_000L, includePrereleases = false)
        assertNotNull(fromPublic130)
        assertEquals(11_000_000L, fromPublic130!!.versionCode)
        assertEquals("11.0.0", fromPublic130.versionName)

        val fromPrivate140Dev1 = selectBetaRelease(feed, repo, installedCode = 10_040_001L, includePrereleases = false)
        assertNotNull(fromPrivate140Dev1)
        assertEquals(11_000_000L, fromPrivate140Dev1!!.versionCode)

        val nextPatchFeed = JSONArray().put(
            JSONObject()
                .put("tag_name", "v11.0.1")
                .put("draft", false)
                .put("prerelease", false)
                .put("name", "duit. v11.0.1")
                .put(
                    "assets",
                    JSONArray().put(
                        JSONObject()
                            .put("name", "duit-11.0.1.apk")
                            .put("state", "uploaded")
                            .put("size", 8_950_000)
                            .put("browser_download_url", "https://github.com/$repo/releases/download/v11.0.1/duit-11.0.1.apk")
                            .put("digest", validDigest)
                    )
                )
        )
        val futurePatch = selectBetaRelease(nextPatchFeed, repo, installedCode = v11Code, includePrereleases = false)
        assertNotNull(futurePatch)
        assertEquals(11_000_001L, futurePatch!!.versionCode)
    }
}
