package cn.verlu.sync.data.mapper

import cn.verlu.sync.data.local.TemperatureLevelEntity
import cn.verlu.sync.data.remote.TemperatureLevelDto
import cn.verlu.sync.domain.model.TemperatureLevel

fun TemperatureLevelEntity.toDomain() = TemperatureLevel(
    userId = userId,
    deviceFriendlyName = deviceFriendlyName,
    deviceModel = deviceModel,
    temperature = temperature,
    updatedAt = updatedAt
)

fun TemperatureLevelDto.toEntity() = TemperatureLevelEntity(
    userId = userId,
    temperature = temperature,
    updatedAt = updatedAt,
    deviceModel = deviceModel,
    deviceFriendlyName = deviceFriendlyName
)

fun TemperatureLevelEntity.toDto() = TemperatureLevelDto(
    userId = userId,
    temperature = temperature,
    updatedAt = updatedAt,
    deviceModel = deviceModel,
    deviceFriendlyName = deviceFriendlyName
)
