package cn.verlu.lulu.domain.sync

interface WeatherStatusSource {
    suspend fun loadWeather(): WeatherStatus
}

interface BatteryStatusReader {
    fun readBatteryStatus(): BatteryStatus
}

interface ScreenTimeStatusSource {
    suspend fun loadScreenTime(): ScreenTimeStatus
}

interface DeviceTemperatureStatusSource {
    suspend fun loadDeviceTemperature(): DeviceTemperatureStatus
}
