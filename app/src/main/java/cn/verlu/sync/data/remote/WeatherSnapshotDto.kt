package cn.verlu.sync.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Supabase 表 `weather_snapshots`（需自行在控制台执行 SQL 建表并配置 RLS，与 `battery_levels` 类似）。
 */
@Serializable
data class WeatherSnapshotDto(
    @SerialName("user_id") val userId: String,
    @SerialName("latitude") val latitude: Double,
    @SerialName("longitude") val longitude: Double,
    @SerialName("city_label") val cityLabel: String,
    @SerialName("temp") val temp: String,
    @SerialName("feels_like") val feelsLike: String = "",
    @SerialName("text_desc") val textDesc: String,
    @SerialName("icon") val icon: String,
    @SerialName("obs_time") val obsTime: String = "",
    @SerialName("api_update_time") val apiUpdateTime: String = "",
    @SerialName("forecast_json") val forecastJson: String = "",
    @SerialName("device_friendly_name") val deviceFriendlyName: String = "",
    @SerialName("updated_at")
    @Serializable(with = EpochMillisFromSupabaseSerializer::class)
    val updatedAt: Long
)
