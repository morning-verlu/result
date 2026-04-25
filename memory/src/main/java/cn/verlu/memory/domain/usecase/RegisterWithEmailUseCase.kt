package cn.verlu.memory.domain.usecase

import cn.verlu.memory.domain.repository.AuthRepository
import javax.inject.Inject

class RegisterWithEmailUseCase @Inject constructor(
    private val repository: AuthRepository,
) {
    suspend operator fun invoke(email: String, password: String) {
        repository.registerWithEmail(email = email, password = password)
    }
}
