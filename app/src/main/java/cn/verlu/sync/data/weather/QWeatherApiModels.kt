package cn.verlu.sync.data.weather

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class QWeatherNowResponse(
    val code: String? = null,
    @SerialName("updateTime") val updateTime: String? = null,
    val now: QWeatherNowPayload? = null
)

@Serializable
data class QWeatherNowPayload(
    @SerialName("obsTime") val obsTime: String? = null,
    val temp: String? = null,
    @SerialName("feelsLike") val feelsLike: String? = null,
    val icon: String? = null,
    val text: String? = null
)

@Serializable
data class QWeatherDailyResponse(
    val code: String? = null,
    val daily: List<QWeatherDailyDay> = emptyList()
)

@Serializable
data class QWeatherDailyDay(
    @SerialName("fxDate") val fxDate: String,
    @SerialName("tempMax") val tempMax: String,
    @SerialName("tempMin") val tempMin: String,
    @SerialName("textDay") val textDay: String,
    @SerialName("iconDay") val iconDay: String
)

@Serializable
data class WeatherEdgeResponse(
    val now: QWeatherNowResponse,
    val daily: QWeatherDailyResponse
)
