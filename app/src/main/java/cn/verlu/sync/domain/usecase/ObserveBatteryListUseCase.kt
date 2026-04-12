package cn.verlu.sync.domain.usecase

import cn.verlu.sync.domain.repository.BatteryRepository
import javax.inject.Inject

class ObserveBatteryListUseCase @Inject constructor(
    private val repository: BatteryRepository
) {
    operator fun invoke() = repository.observeAllBatteries()
}
