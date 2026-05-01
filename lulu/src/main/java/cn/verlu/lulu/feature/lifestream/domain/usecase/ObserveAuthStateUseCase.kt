package cn.verlu.lulu.feature.lifestream.domain.usecase

import cn.verlu.lulu.feature.lifestream.domain.repository.AuthRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveAuthStateUseCase @Inject constructor(
    private val repository: AuthRepository,
) {
    operator fun invoke(): Flow<Boolean> = repository.observeAuthenticated()
}
