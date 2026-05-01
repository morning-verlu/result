package cn.verlu.lulu.domain.sync

data class TodayStatus(
    val weather: WeatherStatus,
    val battery: BatteryStatus,
    val screenTime: ScreenTimeStatus,
    val deviceTemperature: DeviceTemperatureStatus,
)

data class WeatherStatus(
    val temperatureCelsius: Int?,
    val condition: String?,
)

data class BatteryStatus(
    val percent: Int?,
    val isCharging: Boolean,
)

data class ScreenTimeStatus(
    val totalMinutes: Int?,
    val detail: String = "今天",
)

data class DeviceTemperatureStatus(
    val celsius: Float?,
    val label: String?,
)
