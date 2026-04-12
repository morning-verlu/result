package cn.verlu.sync.data.mapper

import cn.verlu.sync.data.local.WeatherSnapshotEntity
import cn.verlu.sync.data.remote.WeatherSnapshotDto
import cn.verlu.sync.data.weather.QWeatherDailyDay
import cn.verlu.sync.domain.model.WeatherDailyRow
import cn.verlu.sync.domain.model.WeatherSnapshot
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val forecastParser = Json { ignoreUnknownKeys = true }

fun WeatherSnapshotDto.toEntity(): WeatherSnapshotEntity =
    WeatherSnapshotEntity(
        userId = userId,
        latitude = latitude,
        longitude = longitude,
        cityLabel = cityLabel,
        temp = temp,
        feelsLike = feelsLike,
        textDesc = textDesc,
        icon = icon,
        obsTime = obsTime,
        apiUpdateTime = apiUpdateTime,
        forecastJson = forecastJson,
        deviceFriendlyName = deviceFriendlyName,
        updatedAt = updatedAt
    )

fun WeatherSnapshotEntity.toDomain(): WeatherSnapshot {
    val daily: List<WeatherDailyRow> = try {
        forecastParser.decodeFromString(
            ListSerializer(QWeatherDailyDay.serializer()),
            forecastJson
        ).map {
            WeatherDailyRow(
                date = it.fxDate,
                tempMin = it.tempMin,
                tempMax = it.tempMax,
                textDay = it.textDay,
                iconDay = it.iconDay
            )
        }
    } catch (_: Exception) {
        emptyList()
    }
    return WeatherSnapshot(
        userId = userId,
        latitude = latitude,
        longitude = longitude,
        cityLabel = cityLabel,
        temp = temp,
        feelsLike = feelsLike,
        textDesc = textDesc,
        icon = icon,
        obsTime = obsTime,
        apiUpdateTime = apiUpdateTime,
        dailyForecast = daily,
        deviceFriendlyName = deviceFriendlyName,
        updatedAt = updatedAt
    )
}
