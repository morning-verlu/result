package cn.verlu.sync.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ScreenTimeReportDto(
    @SerialName("user_id") val userId: String,
    @SerialName("period") val period: String,
    @SerialName("total_foreground_ms") val totalForegroundMs: Long,
    @SerialName("top_apps") val topApps: List<ScreenTimeTopAppDto>,
    @SerialName("updated_at") val updatedAt: Long,
    @SerialName("device_model") val deviceModel: String = "",
    @SerialName("device_friendly_name") val deviceFriendlyName: String = ""
)

@Serializable
data class ScreenTimeTopAppDto(
    @SerialName("label") val label: String,
    @SerialName("package_name") val packageName: String,
    @SerialName("ms") val ms: Long
)
