package cn.verlu.sync.data.repository

import android.util.Log
import cn.verlu.sync.data.device.DeviceModelLabel
import cn.verlu.sync.data.remote.ScreenTimeReportDto
import cn.verlu.sync.data.remote.ScreenTimeTopAppDto
import cn.verlu.sync.domain.model.ScreenTimePeriod
import cn.verlu.sync.domain.model.ScreenTimeSummary
import cn.verlu.sync.domain.repository.ScreenTimeRemoteRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScreenTimeRemoteRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient,
    private val deviceModelLabel: DeviceModelLabel
) : ScreenTimeRemoteRepository {

    override suspend fun uploadReport(period: ScreenTimePeriod, summary: ScreenTimeSummary) {
        val userId = supabase.auth.currentUserOrNull()?.id
            ?: error("未登录，无法同步屏幕时长")
        val dto = ScreenTimeReportDto(
            userId = userId,
            period = period.toRemotePeriod(),
            totalForegroundMs = summary.totalForegroundMillis,
            topApps = summary.topApps.take(3).map {
                ScreenTimeTopAppDto(
                    label = it.appLabel,
                    packageName = it.packageName,
                    ms = it.foregroundMillis
                )
            },
            updatedAt = System.currentTimeMillis(),
            deviceModel = deviceModelLabel.get(),
            deviceFriendlyName = deviceModelLabel.getFriendlyName()
        )
        runCatching {
            supabase.from("screen_time_reports").upsert(dto) {
                onConflict = "user_id,period"
            }
        }.onFailure { e ->
            Log.e(TAG, "screen_time_reports upsert failed", e)
        }
    }

    private fun ScreenTimePeriod.toRemotePeriod(): String = when (this) {
        ScreenTimePeriod.Today -> "today"
        ScreenTimePeriod.Last7Days -> "last_7_days"
    }

    private companion object {
        private const val TAG = "ScreenTimeSync"
    }
}
