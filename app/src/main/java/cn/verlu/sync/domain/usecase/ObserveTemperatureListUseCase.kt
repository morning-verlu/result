package cn.verlu.sync.domain.usecase

import cn.verlu.sync.domain.repository.TemperatureRepository
import javax.inject.Inject

class ObserveTemperatureListUseCase @Inject constructor(
    private val repository: TemperatureRepository
) {
    operator fun invoke() = repository.observeAllTemperatures()
}
