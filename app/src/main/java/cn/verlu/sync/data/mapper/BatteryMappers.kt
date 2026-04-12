package cn.verlu.sync.data.mapper

import cn.verlu.sync.data.local.BatteryLevelEntity
import cn.verlu.sync.data.remote.BatteryLevelDto
import cn.verlu.sync.domain.model.BatteryLevel

fun BatteryLevelEntity.toDomain(): BatteryLevel = BatteryLevel(
    userId = userId,
    deviceFriendlyName = deviceFriendlyName,
    deviceModel = deviceModel,
    batteryPercent = batteryPercent,
    updatedAt = updatedAt
)

fun BatteryLevelDto.toEntity(): BatteryLevelEntity = BatteryLevelEntity(
    userId = userId,
    batteryPercent = batteryPercent,
    updatedAt = updatedAt,
    deviceModel = deviceModel,
    deviceFriendlyName = deviceFriendlyName
)
