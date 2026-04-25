package cn.verlu.memory.domain.usecase

import cn.verlu.memory.domain.repository.AuthRepository
import javax.inject.Inject

class ResetPasswordUseCase @Inject constructor(
    private val repository: AuthRepository,
) {
    suspend operator fun invoke(email: String) {
        repository.resetPassword(email = email)
    }
}
