package cn.verlu.lulu.feature.sync.domain.usecase

import cn.verlu.lulu.feature.sync.domain.repository.BatteryRepository
import javax.inject.Inject

class RefreshBatteryListUseCase @Inject constructor(
    private val repository: BatteryRepository
) {
    suspend operator fun invoke() = repository.refreshFromRemote()
}
