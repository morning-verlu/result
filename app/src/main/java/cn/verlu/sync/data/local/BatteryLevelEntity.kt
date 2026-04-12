package cn.verlu.sync.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "battery_levels")
data class BatteryLevelEntity(
    @PrimaryKey val userId: String,
    val batteryPercent: Int,
    val updatedAt: Long,
    val deviceModel: String = "",
    val deviceFriendlyName: String = ""
)
