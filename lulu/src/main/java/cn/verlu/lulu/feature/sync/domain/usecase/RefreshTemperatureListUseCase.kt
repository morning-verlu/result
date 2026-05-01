package cn.verlu.lulu.feature.sync.domain.usecase

import cn.verlu.lulu.feature.sync.domain.repository.TemperatureRepository
import javax.inject.Inject

class RefreshTemperatureListUseCase @Inject constructor(
    private val repository: TemperatureRepository
) {
    suspend operator fun invoke() = repository.refreshFromRemote()
}
