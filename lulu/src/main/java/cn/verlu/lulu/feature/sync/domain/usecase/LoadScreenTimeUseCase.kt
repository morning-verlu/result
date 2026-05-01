package cn.verlu.lulu.feature.sync.domain.usecase

import cn.verlu.lulu.feature.sync.domain.model.ScreenTimePeriod
import cn.verlu.lulu.feature.sync.domain.model.ScreenTimeSummary
import cn.verlu.lulu.feature.sync.domain.repository.ScreenTimeRepository
import javax.inject.Inject

class LoadScreenTimeUseCase @Inject constructor(
    private val repository: ScreenTimeRepository
) {
    fun hasUsageAccess(): Boolean = repository.hasUsageAccess()

    suspend operator fun invoke(period: ScreenTimePeriod): ScreenTimeSummary =
        repository.loadSummary(period)
}
