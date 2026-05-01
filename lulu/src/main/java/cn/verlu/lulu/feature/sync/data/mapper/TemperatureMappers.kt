package cn.verlu.lulu.feature.sync.data.mapper

import cn.verlu.lulu.feature.sync.data.local.TemperatureLevelEntity
import cn.verlu.lulu.feature.sync.data.remote.TemperatureLevelDto
import cn.verlu.lulu.feature.sync.domain.model.TemperatureLevel

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
