package cn.verlu.sync.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TemperatureLevelDto(
    @SerialName("user_id") val userId: String,
    @SerialName("temperature") val temperature: Int,
    @SerialName("updated_at")
    @Serializable(with = EpochMillisFromSupabaseSerializer::class)
    val updatedAt: Long,
    @SerialName("device_model") val deviceModel: String = "",
    @SerialName("device_friendly_name") val deviceFriendlyName: String = ""
)
