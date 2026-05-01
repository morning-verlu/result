package cn.verlu.lulu.feature.sync.domain.model

data class AppUsageBreakdown(
    val appLabel: String,
    val packageName: String,
    val foregroundMillis: Long
)

data class ScreenTimeSummary(
    val totalForegroundMillis: Long,
    val topApps: List<AppUsageBreakdown>
)
