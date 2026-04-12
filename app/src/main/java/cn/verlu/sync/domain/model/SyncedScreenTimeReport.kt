package cn.verlu.sync.domain.model

data class SyncedScreenTimeReport(
    val rowKey: String,
    /** 与远端 period 一致，用于列表项稳定 key。 */
    val period: String,
    val userId: String,
    val deviceFriendlyName: String,
    val deviceModel: String,
    val totalForegroundMillis: Long,
    val updatedAtMillis: Long,
    val topApps: List<AppUsageBreakdown>
) {
    val stableKey: String get() = "$userId|$period"
}
