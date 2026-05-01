package cn.verlu.lulu.domain.sync

import kotlinx.serialization.Serializable

@Serializable
enum class SyncStatusType {
    Weather,
    Battery,
    ScreenTime,
    DeviceTemperature,
}
