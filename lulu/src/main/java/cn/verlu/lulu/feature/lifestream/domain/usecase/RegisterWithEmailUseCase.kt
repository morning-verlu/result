package cn.verlu.lulu.feature.lifestream.domain.usecase

import cn.verlu.lulu.feature.lifestream.domain.repository.AuthRepository
import javax.inject.Inject

class RegisterWithEmailUseCase @Inject constructor(
    private val repository: AuthRepository,
) {
    suspend operator fun invoke(email: String, password: String) {
        repository.registerWithEmail(email = email, password = password)
    }
}
