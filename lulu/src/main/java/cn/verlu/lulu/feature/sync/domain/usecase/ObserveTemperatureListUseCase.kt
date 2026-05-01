package cn.verlu.lulu.feature.sync.domain.usecase

import cn.verlu.lulu.feature.sync.domain.repository.TemperatureRepository
import javax.inject.Inject

class ObserveTemperatureListUseCase @Inject constructor(
    private val repository: TemperatureRepository
) {
    operator fun invoke() = repository.observeAllTemperatures()
}
