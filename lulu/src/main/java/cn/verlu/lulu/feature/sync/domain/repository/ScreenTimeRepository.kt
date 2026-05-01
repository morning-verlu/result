package cn.verlu.lulu.feature.sync.domain.repository

import cn.verlu.lulu.feature.sync.domain.model.ScreenTimePeriod
import cn.verlu.lulu.feature.sync.domain.model.ScreenTimeSummary

interface ScreenTimeRepository {
    fun hasUsageAccess(): Boolean
    suspend fun loadSummary(period: ScreenTimePeriod): ScreenTimeSummary
}
