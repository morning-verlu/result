package cn.verlu.lulu.feature.sync.domain.usecase

import cn.verlu.lulu.feature.sync.domain.model.ScreenTimePeriod
import cn.verlu.lulu.feature.sync.domain.model.ScreenTimeSummary
import cn.verlu.lulu.feature.sync.domain.repository.ScreenTimeRemoteRepository
import javax.inject.Inject

class UploadScreenTimeReportUseCase @Inject constructor(
    private val remoteRepository: ScreenTimeRemoteRepository
) {
    suspend operator fun invoke(period: ScreenTimePeriod, summary: ScreenTimeSummary) {
        remoteRepository.uploadReport(period, summary)
    }
}
