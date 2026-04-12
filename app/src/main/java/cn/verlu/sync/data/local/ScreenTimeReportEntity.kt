package cn.verlu.sync.data.local

import androidx.room.Entity

@Entity(
    tableName = "screen_time_reports",
    primaryKeys = ["userId", "period"]
)
data class ScreenTimeReportEntity(
    val userId: String,
    val period: String,
    val deviceModel: String,
    val deviceFriendlyName: String = "",
    val totalForegroundMs: Long,
    val topAppsJson: String,
    val updatedAt: Long
)
