package com.diniy.duit.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.diniy.duit.data.local.entity.NotificationHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationHistoryDao {
    @Query("SELECT * FROM notification_history ORDER BY deliveredAtEpochMs DESC, id DESC")
    fun observeAll(): Flow<List<NotificationHistoryEntity>>

    @Query("SELECT COUNT(*) FROM notification_history WHERE isRead = 0")
    fun observeUnreadCount(): Flow<Int>

    @Query("SELECT COUNT(*) > 0 FROM notification_history WHERE dedupKey = :dedupKey")
    suspend fun existsByDedupKey(dedupKey: String): Boolean

    @Query("SELECT DISTINCT notificationId FROM notification_history")
    suspend fun getAllDeliveredNotificationIds(): List<Int>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(entity: NotificationHistoryEntity): Long

    @Query("UPDATE notification_history SET isRead = 1 WHERE id = :id")
    suspend fun markAsRead(id: Long)

    @Query("UPDATE notification_history SET isRead = 1 WHERE isRead = 0")
    suspend fun markAllAsRead()

    @Query("DELETE FROM notification_history WHERE category = :category AND entityId = :entityId AND isRead = 0")
    suspend fun deleteUnreadForEntity(category: String, entityId: Long)

    @Query("DELETE FROM notification_history")
    suspend fun clearAll()
}
