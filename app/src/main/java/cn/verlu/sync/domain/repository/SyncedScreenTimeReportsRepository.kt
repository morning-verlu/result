package cn.verlu.sync.domain.repository

import cn.verlu.sync.domain.model.ScreenTimePeriod
import cn.verlu.sync.domain.model.SyncedScreenTimeReport
import kotlinx.coroutines.flow.Flow

interface SyncedScreenTimeReportsRepository {
    fun observeByPeriod(period: ScreenTimePeriod): Flow<List<SyncedScreenTimeReport>>
    suspend fun refreshFromRemote(period: ScreenTimePeriod)
}
