package cn.verlu.sync.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "temperature_levels")
data class TemperatureLevelEntity(
    @PrimaryKey val userId: String,
    val temperature: Int,
    val updatedAt: Long,
    val deviceModel: String = "",
    val deviceFriendlyName: String = ""
)
