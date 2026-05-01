package cn.verlu.lulu.feature.sync.data.mapper

import cn.verlu.lulu.feature.sync.data.local.ScreenTimeReportEntity
import cn.verlu.lulu.feature.sync.data.remote.ScreenTimeReportDto
import cn.verlu.lulu.feature.sync.data.remote.ScreenTimeTopAppDto
import cn.verlu.lulu.feature.sync.domain.model.AppUsageBreakdown
import cn.verlu.lulu.feature.sync.domain.model.SyncedScreenTimeReport
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

private val topAppsListSerializer = ListSerializer(ScreenTimeTopAppDto.serializer())

fun ScreenTimeReportDto.toEntity(): ScreenTimeReportEntity = ScreenTimeReportEntity(
    userId = userId,
    period = period,
    deviceModel = deviceModel,
    deviceFriendlyName = deviceFriendlyName,
    totalForegroundMs = totalForegroundMs,
    topAppsJson = json.encodeToString(topAppsListSerializer, topApps.take(TOP_APPS_CAP)),
    updatedAt = updatedAt
)

fun ScreenTimeReportEntity.toDomain(): SyncedScreenTimeReport {
    val apps = runCatching {
        json.decodeFromString(topAppsListSerializer, topAppsJson)
            .map { AppUsageBreakdown(it.label, it.packageName, it.ms) }
    }.getOrDefault(emptyList())
    return SyncedScreenTimeReport(
        rowKey = "$userId|$period",
        period = period,
        userId = userId,
        deviceFriendlyName = deviceFriendlyName,
        deviceModel = deviceModel,
        totalForegroundMillis = totalForegroundMs,
        updatedAtMillis = updatedAt,
        topApps = apps.take(TOP_APPS_CAP)
    )
}

private const val TOP_APPS_CAP = 3
