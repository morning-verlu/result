package cn.verlu.sync.data.stats

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import cn.verlu.sync.di.IoDispatcher
import cn.verlu.sync.domain.model.AppUsageBreakdown
import cn.verlu.sync.domain.model.ScreenTimePeriod
import cn.verlu.sync.domain.model.ScreenTimeSummary
import cn.verlu.sync.domain.repository.ScreenTimeRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScreenTimeRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ScreenTimeRepository {

    /**
     * 与官方文档一致：通过 [AppOpsManager] 检查 [AppOpsManager.OPSTR_GET_USAGE_STATS]。
     * 无更新 API 可替代；部分 ROM 仍须在系统「使用情况访问」里手动打开。
     */
    override fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    override suspend fun loadSummary(period: ScreenTimePeriod): ScreenTimeSummary =
        withContext(ioDispatcher) {
            if (!hasUsageAccess()) {
                return@withContext ScreenTimeSummary(0L, emptyList())
            }
            val end = System.currentTimeMillis()
            val begin = when (period) {
                ScreenTimePeriod.Today -> startOfLocalDayMillis(end)
                ScreenTimePeriod.Last7Days -> end - SEVEN_DAYS_MS
            }
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val pm = context.packageManager
            val windowSpan = (end - begin).coerceAtLeast(0L)

            val aggregateFromEvents =
                UsageStatsForegroundAggregator.aggregateForegroundByPackageAndUnionTotal(
                    usageStatsManager = usm,
                    beginTime = begin,
                    endTime = end
                )

            val finalByPackage = aggregateFromEvents.byPackageMillis

            val total = aggregateFromEvents.totalUnionMillis.coerceIn(0L, windowSpan)

            val breakdown = normalizedTopBreakdown(
                pm = pm,
                byPackageMillis = finalByPackage,
                totalForegroundMillis = total
            )

            ScreenTimeSummary(totalForegroundMillis = total, topApps = breakdown)
        }

    /**
     * 展示一致性修正：
     * - 每个 top app 时长不超过总时长
     * - top3 累计不超过总时长
     *
     * Android UsageStats 在部分 ROM / 多窗口场景下会出现分项口径高于并集总时长，
     * 这里在展示层做稳定化，避免出现“某个应用比总时长还长”的违和数据。
     */
    private fun normalizedTopBreakdown(
        pm: PackageManager,
        byPackageMillis: Map<String, Long>,
        totalForegroundMillis: Long
    ): List<AppUsageBreakdown> {
        var remaining = totalForegroundMillis.coerceAtLeast(0L)
        if (remaining == 0L) return emptyList()

        return byPackageMillis.entries
            .asSequence()
            .sortedByDescending { it.value }
            .take(TOP_N)
            .mapNotNull { (pkg, rawMs) ->
                if (remaining <= 0L) return@mapNotNull null
                val ms = rawMs.coerceAtLeast(0L).coerceAtMost(remaining)
                if (ms <= 0L) return@mapNotNull null
                remaining -= ms
                AppUsageBreakdown(
                    appLabel = resolveLabel(pm, pkg),
                    packageName = pkg,
                    foregroundMillis = ms
                )
            }
            .toList()
    }

    private fun resolveLabel(pm: PackageManager, packageName: String): String = runCatching {
        val appInfo = pm.getApplicationInfo(packageName, 0)
        pm.getApplicationLabel(appInfo).toString()
    }.getOrDefault(packageName)

    private fun startOfLocalDayMillis(now: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = now
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private companion object {
        const val SEVEN_DAYS_MS = 7L * 24L * 60L * 60L * 1000L
        const val TOP_N = 3
    }
}
