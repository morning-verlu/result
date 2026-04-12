package cn.verlu.sync.domain.repository

import cn.verlu.sync.domain.model.ScreenTimePeriod
import cn.verlu.sync.domain.model.ScreenTimeSummary

interface ScreenTimeRepository {
    fun hasUsageAccess(): Boolean
    suspend fun loadSummary(period: ScreenTimePeriod): ScreenTimeSummary
}
