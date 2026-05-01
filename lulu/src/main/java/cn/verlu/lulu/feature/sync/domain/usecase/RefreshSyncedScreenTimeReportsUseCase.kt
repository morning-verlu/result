package cn.verlu.lulu.feature.sync.domain.usecase

import cn.verlu.lulu.feature.sync.domain.model.ScreenTimePeriod
import cn.verlu.lulu.feature.sync.domain.repository.SyncedScreenTimeReportsRepository
import javax.inject.Inject

class RefreshSyncedScreenTimeReportsUseCase @Inject constructor(
    private val repository: SyncedScreenTimeReportsRepository
) {
    suspend operator fun invoke(period: ScreenTimePeriod) {
        repository.refreshFromRemote(period)
    }
}
