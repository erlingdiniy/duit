package com.diniy.duit.notifications

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.diniy.duit.data.local.dao.NotificationHistoryDao
import com.diniy.duit.data.local.entity.NotificationHistoryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

interface NotificationPreferencesRepository {
    fun observeSettings(): Flow<NotificationSettingsSnapshot>
    suspend fun setMasterEnabled(enabled: Boolean)
    suspend fun setCategoryEnabled(category: NotificationCategory, enabled: Boolean)
    suspend fun setOffsetEnabled(offset: ReminderOffset, enabled: Boolean)
    suspend fun setBudgetThresholdEnabled(threshold: BudgetAlertThreshold, enabled: Boolean)
    suspend fun setOverdueEnabled(enabled: Boolean)
    suspend fun setReminderTime(hour: Int, minute: Int)
    suspend fun setHideLockScreenAmounts(hide: Boolean)
    suspend fun applyPortableSettings(snapshot: NotificationSettingsSnapshot)
    suspend fun resetDefaults()
}

interface NotificationHistoryRepository {
    fun observeHistory(): Flow<List<NotificationHistoryItem>>
    fun observeUnreadCount(): Flow<Int>
    suspend fun markAsRead(id: Long)
    suspend fun markAllAsRead()
    suspend fun clearHistory()
    suspend fun recordDeliveredIfAbsent(
        dedupKey: String,
        notificationId: Int,
        category: NotificationCategory,
        entityId: Long,
        targetEpochDay: Long,
        title: String,
        message: String,
        amountSen: Long?,
        isOverdue: Boolean,
        deepLink: NotificationDeepLink,
        deliveredAtEpochMs: Long
    ): Boolean
}

private val Context.duitNotificationsDataStore: DataStore<Preferences> by preferencesDataStore(name = "duit_notifications_prefs")

class DataStoreNotificationPreferencesRepository(context: Context) : NotificationPreferencesRepository {

    private val dataStore = context.applicationContext.duitNotificationsDataStore

    private object Keys {
        val MASTER_ENABLED = booleanPreferencesKey("master_enabled")
        val ENABLED_CATEGORIES = stringSetPreferencesKey("enabled_categories")
        val ENABLED_OFFSETS = stringSetPreferencesKey("enabled_offsets")
        val ENABLED_BUDGET_THRESHOLDS = stringSetPreferencesKey("enabled_budget_thresholds")
        val OVERDUE_ENABLED = booleanPreferencesKey("overdue_enabled")
        val REMINDER_HOUR = intPreferencesKey("reminder_hour")
        val REMINDER_MINUTE = intPreferencesKey("reminder_minute")
        val HIDE_LOCK_SCREEN_AMOUNTS = booleanPreferencesKey("hide_lock_screen_amounts")
    }

    override fun observeSettings(): Flow<NotificationSettingsSnapshot> = dataStore.data
        .catch { err ->
            if (err is IOException) emit(emptyPreferences()) else throw err
        }
        .map { prefs ->
            val rawCats = prefs[Keys.ENABLED_CATEGORIES]
            val categories = if (rawCats == null) {
                NotificationCategory.entries.toSet()
            } else {
                rawCats.mapNotNull { name -> NotificationCategory.entries.firstOrNull { it.name == name } }.toSet()
            }

            val rawOffsets = prefs[Keys.ENABLED_OFFSETS]
            val offsets = if (rawOffsets == null) {
                ReminderOffset.entries.toSet()
            } else {
                rawOffsets.mapNotNull { name -> ReminderOffset.entries.firstOrNull { it.name == name } }.toSet()
            }

            val rawThresholds = prefs[Keys.ENABLED_BUDGET_THRESHOLDS]
            val thresholds = if (rawThresholds == null) {
                BudgetAlertThreshold.entries.toSet()
            } else {
                rawThresholds.mapNotNull { name -> BudgetAlertThreshold.entries.firstOrNull { it.name == name } }.toSet()
            }

            NotificationSettingsSnapshot(
                masterEnabled = prefs[Keys.MASTER_ENABLED] ?: true,
                enabledCategories = categories,
                enabledOffsets = offsets,
                enabledBudgetThresholds = thresholds,
                overdueEnabled = prefs[Keys.OVERDUE_ENABLED] ?: true,
                reminderHour = (prefs[Keys.REMINDER_HOUR] ?: 9).coerceIn(0, 23),
                reminderMinute = (prefs[Keys.REMINDER_MINUTE] ?: 0).coerceIn(0, 59),
                hideLockScreenAmounts = prefs[Keys.HIDE_LOCK_SCREEN_AMOUNTS] ?: true
            )
        }

    override suspend fun setMasterEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.MASTER_ENABLED] = enabled }
    }

    override suspend fun setCategoryEnabled(category: NotificationCategory, enabled: Boolean) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.ENABLED_CATEGORIES]
                ?: NotificationCategory.entries.map { it.name }.toSet()
            prefs[Keys.ENABLED_CATEGORIES] = if (enabled) current + category.name else current - category.name
        }
    }

    override suspend fun setOffsetEnabled(offset: ReminderOffset, enabled: Boolean) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.ENABLED_OFFSETS]
                ?: ReminderOffset.entries.map { it.name }.toSet()
            prefs[Keys.ENABLED_OFFSETS] = if (enabled) current + offset.name else current - offset.name
        }
    }

    override suspend fun setBudgetThresholdEnabled(threshold: BudgetAlertThreshold, enabled: Boolean) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.ENABLED_BUDGET_THRESHOLDS]
                ?: BudgetAlertThreshold.entries.map { it.name }.toSet()
            prefs[Keys.ENABLED_BUDGET_THRESHOLDS] = if (enabled) current + threshold.name else current - threshold.name
        }
    }

    override suspend fun setOverdueEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.OVERDUE_ENABLED] = enabled }
    }

    override suspend fun setReminderTime(hour: Int, minute: Int) {
        dataStore.edit { prefs ->
            prefs[Keys.REMINDER_HOUR] = hour.coerceIn(0, 23)
            prefs[Keys.REMINDER_MINUTE] = minute.coerceIn(0, 59)
        }
    }

    override suspend fun setHideLockScreenAmounts(hide: Boolean) {
        dataStore.edit { it[Keys.HIDE_LOCK_SCREEN_AMOUNTS] = hide }
    }

    override suspend fun applyPortableSettings(snapshot: NotificationSettingsSnapshot) {
        dataStore.edit { prefs ->
            prefs[Keys.MASTER_ENABLED] = snapshot.masterEnabled
            prefs[Keys.ENABLED_CATEGORIES] = snapshot.enabledCategories.map { it.name }.toSet()
            prefs[Keys.ENABLED_OFFSETS] = snapshot.enabledOffsets.map { it.name }.toSet()
            prefs[Keys.ENABLED_BUDGET_THRESHOLDS] = snapshot.enabledBudgetThresholds.map { it.name }.toSet()
            prefs[Keys.OVERDUE_ENABLED] = snapshot.overdueEnabled
            prefs[Keys.REMINDER_HOUR] = snapshot.reminderHour.coerceIn(0, 23)
            prefs[Keys.REMINDER_MINUTE] = snapshot.reminderMinute.coerceIn(0, 59)
            prefs[Keys.HIDE_LOCK_SCREEN_AMOUNTS] = snapshot.hideLockScreenAmounts
        }
    }

    override suspend fun resetDefaults() {
        dataStore.edit { it.clear() }
    }
}

class RoomNotificationHistoryRepository(
    private val dao: NotificationHistoryDao
) : NotificationHistoryRepository {

    override fun observeHistory(): Flow<List<NotificationHistoryItem>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeUnreadCount(): Flow<Int> = dao.observeUnreadCount()

    override suspend fun markAsRead(id: Long) = dao.markAsRead(id)

    override suspend fun markAllAsRead() = dao.markAllAsRead()

    override suspend fun clearHistory() = dao.clearAll()

    override suspend fun recordDeliveredIfAbsent(
        dedupKey: String,
        notificationId: Int,
        category: NotificationCategory,
        entityId: Long,
        targetEpochDay: Long,
        title: String,
        message: String,
        amountSen: Long?,
        isOverdue: Boolean,
        deepLink: NotificationDeepLink,
        deliveredAtEpochMs: Long
    ): Boolean {
        val insertedId = dao.insertIgnore(
            NotificationHistoryEntity(
                dedupKey = dedupKey,
                notificationId = notificationId,
                category = category.name,
                entityId = entityId,
                targetEpochDay = targetEpochDay,
                title = title,
                message = message,
                amountSen = amountSen,
                isOverdue = isOverdue,
                isRead = false,
                deepLink = deepLink.name,
                deliveredAtEpochMs = deliveredAtEpochMs
            )
        )
        return insertedId > 0L
    }
}
