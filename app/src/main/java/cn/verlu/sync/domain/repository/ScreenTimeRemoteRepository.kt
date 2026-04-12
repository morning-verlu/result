package cn.verlu.sync.domain.repository

import cn.verlu.sync.domain.model.ScreenTimePeriod
import cn.verlu.sync.domain.model.ScreenTimeSummary

interface ScreenTimeRemoteRepository {
    suspend fun uploadReport(period: ScreenTimePeriod, summary: ScreenTimeSummary)
}
