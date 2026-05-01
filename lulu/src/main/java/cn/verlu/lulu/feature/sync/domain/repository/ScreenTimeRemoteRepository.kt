package cn.verlu.lulu.feature.sync.domain.repository

import cn.verlu.lulu.feature.sync.domain.model.ScreenTimePeriod
import cn.verlu.lulu.feature.sync.domain.model.ScreenTimeSummary

interface ScreenTimeRemoteRepository {
    suspend fun uploadReport(period: ScreenTimePeriod, summary: ScreenTimeSummary)
}
