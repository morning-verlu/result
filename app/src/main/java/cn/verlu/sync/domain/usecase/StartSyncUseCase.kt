package cn.verlu.sync.domain.usecase

import cn.verlu.sync.domain.repository.BatteryRepository
import javax.inject.Inject

class StartSyncUseCase @Inject constructor(
    private val repository: BatteryRepository
) {
    suspend operator fun invoke() = repository.startSync()
}
