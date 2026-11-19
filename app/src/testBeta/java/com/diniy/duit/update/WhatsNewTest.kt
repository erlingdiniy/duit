package com.diniy.duit.update

import androidx.test.core.app.ApplicationProvider
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = android.app.Application::class)
class WhatsNewTest {

    @Test
    fun showOncePerVersion_onlyAfterARealUpgrade() {
        assertFalse(shouldShowWhatsNew(acknowledged = null, installedCode = 1, freshInstall = true)) // first install
        assertTrue(shouldShowWhatsNew(acknowledged = null, installedCode = 3, freshInstall = false)) // upgraded from a pre-feature build
        assertTrue(shouldShowWhatsNew(acknowledged = 2, installedCode = 3, freshInstall = false))   // genuine upgrade
        assertFalse(shouldShowWhatsNew(acknowledged = 3, installedCode = 3, freshInstall = false))  // already seen: restart shows nothing
        assertFalse(shouldShowWhatsNew(acknowledged = 4, installedCode = 3, freshInstall = false))  // never for an older build
    }

    @Test
    fun acknowledgementAndNotesCache_persistAcrossInstances() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val first = UpdateMemory(context)
        assertNull(first.acknowledgedVersionCode)
        first.acknowledgedVersionCode = 3
        first.cacheNotes(3, "## Bug fixes\n- Crash")
        val reopened = UpdateMemory(context) // e.g. after process death
        assertEquals(3L, reopened.acknowledgedVersionCode)
        assertEquals("## Bug fixes\n- Crash", reopened.cachedNotes(3))
        assertNull(reopened.cachedNotes(4))
    }

    @Test
    fun notesAreMatchedToTheExactInstalledBuild_neverTheLatest() {
        fun rel(tag: String, body: String, draft: Boolean = false) =
            JSONObject().put("tag_name", tag).put("body", body).put("draft", draft).put("prerelease", true)
        val releases = JSONArray()
            .put(rel("beta-4", "four")).put(rel("beta-3", "three")).put(rel("beta-2", "two"))
            .put(rel("beta-5", "draft five", draft = true))
        assertEquals("three", findReleaseNotes(releases, 3))
        assertNull(findReleaseNotes(releases, 5)) // drafts are ignored
        assertNull(findReleaseNotes(releases, 9)) // missing: the UI shows its friendly fallback
    }
}
