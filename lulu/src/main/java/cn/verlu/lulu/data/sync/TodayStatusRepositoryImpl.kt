package cn.verlu.lulu.data.sync

import cn.verlu.lulu.domain.sync.BatteryStatus
import cn.verlu.lulu.domain.sync.BatteryStatusReader
import cn.verlu.lulu.domain.sync.DeviceTemperatureStatus
import cn.verlu.lulu.domain.sync.DeviceTemperatureStatusSource
import cn.verlu.lulu.domain.sync.ScreenTimeStatus
import cn.verlu.lulu.domain.sync.ScreenTimeStatusSource
import cn.verlu.lulu.domain.sync.SyncStatusRepository
import cn.verlu.lulu.domain.sync.TodayStatus
import cn.verlu.lulu.domain.sync.WeatherStatus
import cn.verlu.lulu.domain.sync.WeatherStatusSource
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class TodayStatusRepositoryImpl @Inject constructor(
    private val weatherStatusSource: WeatherStatusSource,
    private val batteryStatusReader: BatteryStatusReader,
    private val screenTimeStatusSource: ScreenTimeStatusSource,
    private val deviceTemperatureStatusSource: DeviceTemperatureStatusSource,
) : SyncStatusRepository {
    override suspend fun loadTodayStatus(): TodayStatus = coroutineScope {
        val weather = async {
            runCatching { weatherStatusSource.loadWeather() }
                .getOrDefault(WeatherStatus(temperatureCelsius = null, condition = "天气不可用"))
        }
        val battery = async {
            runCatching { batteryStatusReader.readBatteryStatus() }
                .getOrDefault(BatteryStatus(percent = null, isCharging = false))
        }
        val screenTime = async {
            runCatching { screenTimeStatusSource.loadScreenTime() }
                .getOrDefault(ScreenTimeStatus(totalMinutes = null, detail = "不可用"))
        }
        val deviceTemperature = async {
            runCatching { deviceTemperatureStatusSource.loadDeviceTemperature() }
                .getOrDefault(DeviceTemperatureStatus(celsius = null, label = "不可用"))
        }

        TodayStatus(
            weather = weather.await(),
            battery = battery.await(),
            screenTime = screenTime.await(),
            deviceTemperature = deviceTemperature.await(),
        )
    }
}
