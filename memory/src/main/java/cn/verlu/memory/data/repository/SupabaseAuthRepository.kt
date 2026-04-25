package cn.verlu.memory.data.repository

import cn.verlu.memory.domain.repository.AuthRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val EMAIL_AUTH_REDIRECT = "memoryapp://login"

class SupabaseAuthRepository @Inject constructor(
    private val supabase: SupabaseClient,
) : AuthRepository {
    override fun observeAuthenticated(): Flow<Boolean> =
        supabase.auth.sessionStatus.map { it is SessionStatus.Authenticated }

    override suspend fun loginWithEmail(email: String, password: String) {
        supabase.auth.signInWith(Email, redirectUrl = EMAIL_AUTH_REDIRECT) {
            this.email = email
            this.password = password
        }
    }

    override suspend fun registerWithEmail(email: String, password: String) {
        supabase.auth.signUpWith(Email, redirectUrl = EMAIL_AUTH_REDIRECT) {
            this.email = email
            this.password = password
        }
    }

    override suspend fun resetPassword(email: String) {
        supabase.auth.resetPasswordForEmail(
            email = email,
            redirectUrl = "$EMAIL_AUTH_REDIRECT?type=recovery",
        )
    }

    override suspend fun signOut() {
        supabase.auth.signOut()
    }
}
