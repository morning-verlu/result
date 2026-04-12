package cn.verlu.sync.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "weather_snapshots")
data class WeatherSnapshotEntity(
    @PrimaryKey val userId: String,
    val latitude: Double,
    val longitude: Double,
    val cityLabel: String,
    val temp: String,
    val feelsLike: String,
    val textDesc: String,
    val icon: String,
    val obsTime: String,
    val apiUpdateTime: String,
    val forecastJson: String,
    val deviceFriendlyName: String,
    val updatedAt: Long
)
