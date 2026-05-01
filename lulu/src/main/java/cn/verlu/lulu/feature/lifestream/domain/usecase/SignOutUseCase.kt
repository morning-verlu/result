package cn.verlu.lulu.feature.lifestream.domain.usecase

import cn.verlu.lulu.feature.lifestream.domain.repository.AuthRepository
import javax.inject.Inject

class SignOutUseCase @Inject constructor(
    private val repository: AuthRepository,
) {
    suspend operator fun invoke() {
        repository.signOut()
    }
}
