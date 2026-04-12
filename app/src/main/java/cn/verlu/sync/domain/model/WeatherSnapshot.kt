package cn.verlu.sync.domain.model

data class WeatherSnapshot(
    val userId: String,
    val latitude: Double,
    val longitude: Double,
    val cityLabel: String,
    val temp: String,
    val feelsLike: String,
    val textDesc: String,
    val icon: String,
    val obsTime: String,
    val apiUpdateTime: String,
    val dailyForecast: List<WeatherDailyRow>,
    val deviceFriendlyName: String,
    val updatedAt: Long
)

data class WeatherDailyRow(
    val date: String,
    val tempMin: String,
    val tempMax: String,
    val textDay: String,
    val iconDay: String
)
