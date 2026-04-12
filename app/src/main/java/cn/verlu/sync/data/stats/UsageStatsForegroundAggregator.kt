package cn.verlu.sync.data.stats

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager

/**
 * 工业级屏幕时间统计：同时采用「物理亮屏锚点」和「事件流叠加」模型。
 */
internal object UsageStatsForegroundAggregator {

    data class ForegroundAggregate(
        val byPackageMillis: Map<String, Long>,
        /** 前台窗口“时间并集”的总时长，避免不同包重叠时把总时长重复算进去。 */
        val totalUnionMillis: Long
    )

    fun aggregateForegroundByPackageAndUnionTotal(
        usageStatsManager: UsageStatsManager,
        beginTime: Long,
        endTime: Long
    ): ForegroundAggregate {
        val windowSpan = (endTime - beginTime).coerceAtLeast(0L)
        if (windowSpan == 0L) {
            return ForegroundAggregate(emptyMap(), 0L)
        }

        // --- 1. 获取系统底层汇总作为基础保底 (Baseline) ---
        val systemStatsMap = usageStatsManager.queryAndAggregateUsageStats(beginTime, endTime)
        val baselineTotalsByPackage = systemStatsMap.mapValues { it.value.totalTimeInForeground }

        // --- 2. 基于事件流深度解析 (Event-driven Analysis) ---
        val usageEvents = usageStatsManager.queryEvents(beginTime, endTime) ?: return ForegroundAggregate(
            byPackageMillis = baselineTotalsByPackage,
            totalUnionMillis = 0L
        )

        val event = UsageEvents.Event()
        val eventTotalsByPackage = mutableMapOf<String, Long>()
        val pkgOpenStart = mutableMapOf<String, Long>()
        val activePackages = mutableSetOf<String>()

        var unionOpenStart: Long? = null
        var totalUnion = 0L

        // 核心锚点：独立统计物理亮屏时长
        var totalScreenOn = 0L
        var screenOnStart: Long? = null

        fun clampSegmentToWindow(start: Long, end: Long): Long {
            val s = maxOf(start, beginTime)
            val e = minOf(end, endTime)
            return (e - s).coerceAtLeast(0L)
        }

        fun closeUnionSegment(unionEnd: Long) {
            val startUnion = unionOpenStart ?: return
            val delta = clampSegmentToWindow(startUnion, unionEnd)
            if (delta > 0L) totalUnion += delta
            unionOpenStart = null
        }

        fun closeScreenOnSegment(screenEnd: Long) {
            val start = screenOnStart ?: return
            val delta = clampSegmentToWindow(start, screenEnd)
            if (delta > 0L) totalScreenOn += delta
            screenOnStart = null
        }

        fun closeAllActive(timestamp: Long, clearPackages: Boolean = true) {
            for (p in activePackages.toList()) {
                val start = pkgOpenStart.remove(p)
                if (start != null) {
                    val delta = clampSegmentToWindow(start, timestamp)
                    if (delta > 0L) {
                        eventTotalsByPackage[p] = eventTotalsByPackage.getOrDefault(p, 0L) + delta
                    }
                }
            }
            if (clearPackages) activePackages.clear()
            closeUnionSegment(timestamp)
        }

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            val pkg = event.packageName
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    val p = pkg ?: continue
                    if (p in pkgOpenStart) continue
                    
                    if (activePackages.isEmpty() && unionOpenStart == null) {
                        unionOpenStart = event.timeStamp
                    }
                    pkgOpenStart[p] = event.timeStamp
                    activePackages.add(p)
                }

                UsageEvents.Event.ACTIVITY_PAUSED -> {
                    val p = pkg ?: continue
                    val start = pkgOpenStart.remove(p)
                    if (start != null) {
                        val delta = clampSegmentToWindow(start, event.timeStamp)
                        if (delta > 0L) {
                            eventTotalsByPackage[p] = eventTotalsByPackage.getOrDefault(p, 0L) + delta
                        }
                    }
                    // 延迟清理逻辑：不在这里 remove(p)，交给真正退出或熄屏
                }

                UsageEvents.Event.SCREEN_NON_INTERACTIVE,
                UsageEvents.Event.KEYGUARD_SHOWN -> {
                    closeAllActive(event.timeStamp, clearPackages = false)
                    closeScreenOnSegment(event.timeStamp)
                }

                UsageEvents.Event.SCREEN_INTERACTIVE -> {
                    screenOnStart = event.timeStamp
                    for (p in activePackages) {
                        pkgOpenStart[p] = event.timeStamp
                    }
                    if (activePackages.isNotEmpty()) {
                        unionOpenStart = event.timeStamp
                    }
                }

                23 -> { // ACTIVITY_STOPPED
                    val p = pkg ?: continue
                    val start = pkgOpenStart.remove(p)
                    if (start != null) {
                        val delta = clampSegmentToWindow(start, event.timeStamp)
                        if (delta > 0L) {
                            eventTotalsByPackage[p] = eventTotalsByPackage.getOrDefault(p, 0L) + delta
                        }
                    }
                    activePackages.remove(p)
                    if (activePackages.isEmpty()) {
                        closeUnionSegment(event.timeStamp)
                    }
                }
            }
        }

        closeAllActive(endTime, clearPackages = true)
        closeScreenOnSegment(endTime)

        // --- 3. 最终融合决策 (Fusion Decision) ---
        val finalMap = mutableMapOf<String, Long>()
        val allPackageNames = baselineTotalsByPackage.keys + eventTotalsByPackage.keys
        for (pkgName in allPackageNames) {
            val baseVal = baselineTotalsByPackage[pkgName] ?: 0L
            val eventVal = eventTotalsByPackage[pkgName] ?: 0L
            finalMap[pkgName] = maxOf(baseVal, eventVal).coerceAtMost(windowSpan)
        }

        // 物理锚点修正：总时长 = max(并发并集时长, 物理亮屏时长 * 权重系数)
        val anchorUnion = (totalScreenOn * 0.95).toLong()
        val finalUnionMillis = maxOf(totalUnion, anchorUnion).coerceAtMost(windowSpan)

        return ForegroundAggregate(finalMap, finalUnionMillis)
    }
}
