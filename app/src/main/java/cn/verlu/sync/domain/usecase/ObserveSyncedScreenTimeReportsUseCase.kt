package cn.verlu.sync.domain.usecase

import cn.verlu.sync.domain.model.ScreenTimePeriod
import cn.verlu.sync.domain.model.SyncedScreenTimeReport
import cn.verlu.sync.domain.repository.SyncedScreenTimeReportsRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveSyncedScreenTimeReportsUseCase @Inject constructor(
    private val repository: SyncedScreenTimeReportsRepository
) {
    operator fun invoke(period: ScreenTimePeriod): Flow<List<SyncedScreenTimeReport>> =
        repository.observeByPeriod(period)
}
