package cn.verlu.memory.domain.usecase

import cn.verlu.memory.domain.repository.AuthRepository
import javax.inject.Inject

class SignOutUseCase @Inject constructor(
    private val repository: AuthRepository,
) {
    suspend operator fun invoke() {
        repository.signOut()
    }
}
