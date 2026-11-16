package com.diniy.duit.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.diniy.duit.notifications.NotificationCategory
import com.diniy.duit.notifications.NotificationDeepLink
import com.diniy.duit.notifications.NotificationHistoryItem

@Entity(
    tableName = "notification_history",
    indices = [
        Index(value = ["dedupKey"], unique = true),
        Index(value = ["category", "entityId"]),
        Index(value = ["isRead", "deliveredAtEpochMs"])
    ]
)
data class NotificationHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val dedupKey: String,
    val notificationId: Int,
    val category: String,
    val entityId: Long,
    val targetEpochDay: Long,
    val title: String,
    val message: String,
    val amountSen: Long?,
    val isOverdue: Boolean,
    val isRead: Boolean,
    val deepLink: String,
    val deliveredAtEpochMs: Long
) {
    fun toDomain(): NotificationHistoryItem = NotificationHistoryItem(
        id = id,
        dedupKey = dedupKey,
        notificationId = notificationId,
        category = NotificationCategory.entries.firstOrNull { it.name == category } ?: NotificationCategory.COMMITMENTS,
        entityId = entityId,
        targetEpochDay = targetEpochDay,
        title = title,
        message = message,
        amountSen = amountSen,
        isOverdue = isOverdue,
        isRead = isRead,
        deepLink = NotificationDeepLink.entries.firstOrNull { it.name == deepLink } ?: NotificationDeepLink.PLANNING_COMMITMENTS,
        deliveredAtEpochMs = deliveredAtEpochMs
    )
}
