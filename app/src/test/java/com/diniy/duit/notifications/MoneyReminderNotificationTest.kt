package com.diniy.duit.notifications

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.diniy.duit.backup.BackupCryptoEngine
import com.diniy.duit.backup.BackupSerializer
import com.diniy.duit.backup.PortablePreferencesBackup
import com.diniy.duit.backup.PortablePrivacyPreferences
import com.diniy.duit.data.local.dao.LedgerDatabaseSnapshot
import com.diniy.duit.domain.calculator.ReminderCalculator
import com.diniy.duit.domain.model.AppSettingsSnapshot
import com.diniy.duit.domain.model.CommitmentFrequency
import com.diniy.duit.domain.model.MoneyReminder
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MoneyReminderNotificationTest {
    private val kl = ZoneId.of("Asia/Kuala_Lumpur")
    private val settings = NotificationSettingsSnapshot(reminderHour = 9, reminderMinute = 0)
    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day).toEpochDay()
    private fun at(y: Int, m: Int, day: Int, h: Int) = ZonedDateTime.of(y, m, day, h, 0, 0, 0, kl).toInstant()

    private fun eval(reminders: List<MoneyReminder>, nowH: Int = 8, s: NotificationSettingsSnapshot = settings) =
        ReminderCalculator.evaluate(s, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), null, emptyList(),
            nowInstant = at(2026, 11, 21, nowH), zoneId = kl, moneyReminders = reminders)

    @Test
    fun once_firesAtReminderTimeOnItsDay_andNeverForPastDays() {
        val today = MoneyReminder(1, "Check bonus", d(2026, 11, 21))
        val past = MoneyReminder(2, "Old", d(2026, 11, 20))
        val before = eval(listOf(today, past), nowH = 8)
        assertTrue(before.dueNowCandidates.isEmpty())
        assertEquals(at(2026, 11, 21, 9).toEpochMilli(), before.nextScheduledTriggerEpochMs)
        assertEquals(listOf("MONEY_REMINDERS:1:${d(2026, 11, 21)}"), before.upcomingCandidates.map { it.dedupKey })

        val after = eval(listOf(today, past), nowH = 10)
        val c = after.dueNowCandidates.single()
        assertEquals(NotificationCategory.MONEY_REMINDERS, c.category)
        assertEquals(NotificationDeepLink.CALENDAR, c.deepLink)
        assertNull(c.amountSen)
        assertTrue(after.dueNowCandidates.none { it.entityId == 2L })
    }

    @Test
    fun weeklyMonthlyYearly_handleMonthEndsAndLeapYears() {
        assertEquals(listOf(21, 28).map { d(2026, 11, it) },
            MoneyReminder(1, "w", d(2026, 11, 14), CommitmentFrequency.WEEKLY).occurrencesBetween(d(2026, 11, 21), d(2026, 11, 30)))
        // 31 Jan monthly -> 28 Feb -> 31 Mar (anchor kept)
        assertEquals(listOf(d(2027, 1, 31), d(2027, 2, 28), d(2027, 3, 31)),
            MoneyReminder(1, "m", d(2027, 1, 31), CommitmentFrequency.MONTHLY).occurrencesBetween(d(2027, 1, 1), d(2027, 3, 31)))
        // 29 Feb 2028 yearly -> 28 Feb 2029
        assertEquals(listOf(d(2028, 2, 29), d(2029, 2, 28)),
            MoneyReminder(1, "y", d(2028, 2, 29), CommitmentFrequency.YEARLY).occurrencesBetween(d(2028, 1, 1), d(2029, 12, 31)))
        val upcoming = eval(listOf(MoneyReminder(3, "Weekly", d(2026, 11, 14), CommitmentFrequency.WEEKLY))).upcomingCandidates
        assertEquals(listOf(d(2026, 11, 21), d(2026, 11, 28), d(2026, 12, 5)), upcoming.map { it.targetEpochDay })
    }

    @Test
    fun edit_reschedules_andDelete_dropsTheActiveId() {
        val original = MoneyReminder(5, "Road tax", d(2026, 11, 22))
        val id = ReminderCalculator.stableNotificationId(NotificationCategory.MONEY_REMINDERS, 5)
        assertTrue(id in eval(listOf(original)).activeNotificationIds)
        val edited = eval(listOf(original.copy(dateEpochDay = d(2026, 11, 25)))).upcomingCandidates.single()
        assertEquals("MONEY_REMINDERS:5:${d(2026, 11, 25)}", edited.dedupKey)
        // Deleted: the id is no longer active, so the sync cancels any shown notification.
        assertFalse(id in eval(emptyList()).activeNotificationIds)
    }

    @Test
    fun disabledCategoryOrMaster_postsNothing_andKeysAreStable() {
        val r = listOf(MoneyReminder(1, "x", d(2026, 11, 21)))
        assertTrue(eval(r, 10, settings.copy(enabledCategories = settings.enabledCategories - NotificationCategory.MONEY_REMINDERS)).dueNowCandidates.isEmpty())
        assertTrue(eval(r, 10, settings.copy(masterEnabled = false)).dueNowCandidates.isEmpty())
        // Same reminder and day give the same dedup key, so history blocks a second post.
        assertEquals(eval(r, 10).dueNowCandidates.single().dedupKey, eval(r, 11).dueNowCandidates.single().dedupKey)
        assertEquals(1, eval(r + r, 10).dueNowCandidates.map { it.dedupKey }.distinct().size)
    }

    @Test
    fun permissionDenied_postsNothing_andGrantedTapOpensTheCalendarDate() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val helper = AndroidNotificationHelper(context)
        val nm = shadowOf(context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
        val candidate = eval(listOf(MoneyReminder(9, "Pay zakat", d(2026, 11, 21))), 10).dueNowCandidates.single()

        shadowOf(context as Application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        helper.postReminder(candidate, maskSensitiveContent = false)
        assertTrue(nm.allNotifications.isEmpty())

        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        helper.postReminder(candidate, maskSensitiveContent = false)
        val tap = shadowOf(nm.getNotification(candidate.notificationId).contentIntent).savedIntent
        assertEquals(NotificationDeepLink.CALENDAR.name, tap.getStringExtra(AndroidNotificationHelper.EXTRA_DEEP_LINK_TARGET))
        assertEquals(d(2026, 11, 21), tap.getLongExtra(AndroidNotificationHelper.EXTRA_TARGET_EPOCH_DAY, -1))
    }

    @Test
    fun eachTap_carriesItsOwnOccurrenceDate_acrossYearEnd_andIsNeverOverwritten() {
        val context: Context = ApplicationProvider.getApplicationContext()
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val helper = AndroidNotificationHelper(context)
        val nm = shadowOf(context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
        val yearEnd = d(2026, 12, 31)
        fun dueOn(day: Long, reminder: MoneyReminder) = ReminderCalculator.evaluate(settings, emptyList(), emptyList(), emptyList(),
            emptyList(), emptyList(), null, emptyList(), nowInstant = ZonedDateTime.of(LocalDate.ofEpochDay(day).atTime(10, 0), kl).toInstant(),
            zoneId = kl, moneyReminders = listOf(reminder)).dueNowCandidates.single()

        val a = dueOn(yearEnd, MoneyReminder(1, "Year-end savings", yearEnd))
        val b = dueOn(d(2027, 1, 1), MoneyReminder(2, "New year budget", d(2027, 1, 1)))
        helper.postReminder(a, false)
        helper.postReminder(b, false)
        val tapA = shadowOf(nm.getNotification(a.notificationId).contentIntent).savedIntent
        val tapB = shadowOf(nm.getNotification(b.notificationId).contentIntent).savedIntent
        assertEquals(yearEnd, com.diniy.duit.calendarEpochDayOf(tapA))
        assertEquals(d(2027, 1, 1), com.diniy.duit.calendarEpochDayOf(tapB))
        assertTrue(tapA.data != tapB.data) // distinct PendingIntents: FLAG_UPDATE_CURRENT can't swap their dates

        // The same weekly reminder's next occurrence replaces its notification with the new date.
        val weekly = MoneyReminder(3, "Weekly check", d(2026, 12, 24), CommitmentFrequency.WEEKLY)
        helper.postReminder(dueOn(d(2026, 12, 24), weekly), false)
        val next = dueOn(d(2026, 12, 31), weekly)
        helper.postReminder(next, false)
        assertEquals(yearEnd, com.diniy.duit.calendarEpochDayOf(shadowOf(nm.getNotification(next.notificationId).contentIntent).savedIntent))
    }

    @Test
    fun intentWithoutDate_givesNoDate() {
        assertNull(com.diniy.duit.calendarEpochDayOf(android.content.Intent()))
    }

    @Test
    fun existingUsers_andOlderBackups_keepMoneyRemindersOn() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val prefs = DataStoreNotificationPreferencesRepository(context)
        // A set written knowing the category exists is respected as-is.
        prefs.applyPortableSettings(NotificationSettingsSnapshot(enabledCategories = setOf(NotificationCategory.COMMITMENTS)))
        prefs.setCategoryEnabled(NotificationCategory.COMMITMENTS, true)
        assertTrue(NotificationCategory.MONEY_REMINDERS !in prefs.observeSettings().first().enabledCategories)
        prefs.setCategoryEnabled(NotificationCategory.MONEY_REMINDERS, true)
        assertTrue(NotificationCategory.MONEY_REMINDERS in prefs.observeSettings().first().enabledCategories)

        val oldPrefs = PortablePreferencesBackup(AppSettingsSnapshot(),
            NotificationSettingsSnapshot(enabledCategories = setOf(NotificationCategory.COMMITMENTS)), PortablePrivacyPreferences())
        val empty = LedgerDatabaseSnapshot(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
            emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        val json = BackupSerializer.encodePayload(empty, oldPrefs, createdAtEpochMs = 1, dbSchemaVersion = 9)
        val bytes = BackupCryptoEngine.encrypt(json, "Pass1234".toCharArray(), dbSchemaVersion = 9)
        val restored = BackupSerializer.decodePayload(BackupCryptoEngine.inspectEnvelope(bytes), BackupCryptoEngine.decrypt(bytes, "Pass1234".toCharArray()))
        assertTrue(NotificationCategory.MONEY_REMINDERS in restored.preferences.notificationSettings.enabledCategories)
        assertTrue(JSONObject(json).getJSONObject("ledger").getJSONArray("transactions").length() == 0)
    }
}
