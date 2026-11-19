package com.diniy.duit.update

import android.content.Context

/**
 * Beta-only update memory, kept in its own small preferences file so it never touches the app's
 * financial DataStore. Holds the last acknowledged build, cached release notes and the last check.
 */
internal class UpdateMemory(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("duit_beta_updates", Context.MODE_PRIVATE)

    var acknowledgedVersionCode: Long?
        get() = prefs.getLong(KEY_ACK, -1L).takeIf { it >= 0 }
        set(value) = prefs.edit().putLong(KEY_ACK, value ?: -1L).apply()

    fun cachedNotes(versionCode: Long): String? = prefs.getString("$KEY_NOTES$versionCode", null)

    fun cacheNotes(versionCode: Long, notes: String) {
        prefs.edit().putString("$KEY_NOTES$versionCode", notes).apply()
    }

    var lastCheckEpochMs: Long
        get() = prefs.getLong(KEY_CHECK_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_CHECK_AT, value).apply()

    var lastCheckResult: String
        get() = prefs.getString(KEY_CHECK_RESULT, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_CHECK_RESULT, value).apply()

    private companion object {
        const val KEY_ACK = "acknowledged_version_code"
        const val KEY_NOTES = "notes_"
        const val KEY_CHECK_AT = "last_check_at"
        const val KEY_CHECK_RESULT = "last_check_result"
    }
}

/**
 * The installed build is the source of truth. A fresh install is never "an update"; an install that
 * predates this feature (no acknowledgement yet, but updated before) counts as one.
 */
internal fun shouldShowWhatsNew(acknowledged: Long?, installedCode: Long, freshInstall: Boolean): Boolean =
    if (acknowledged == null) !freshInstall else installedCode > acknowledged
