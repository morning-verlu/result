package cn.verlu.memory.domain.repository

import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    fun observeAuthenticated(): Flow<Boolean>

    suspend fun loginWithEmail(email: String, password: String)

    suspend fun registerWithEmail(email: String, password: String)

    suspend fun resetPassword(email: String)

    suspend fun signOut()
}
