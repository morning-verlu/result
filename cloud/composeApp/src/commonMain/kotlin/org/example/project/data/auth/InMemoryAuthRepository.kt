package cn.verlu.cloud.data.auth

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import cn.verlu.cloud.domain.auth.AuthSessionState
import cn.verlu.cloud.domain.auth.AuthUser
import cn.verlu.cloud.domain.auth.DesktopQrSession
import cn.verlu.cloud.domain.auth.OAuthProvider
import cn.verlu.cloud.domain.auth.QrApprovalResult
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * In-memory auth implementation for architecture stage:
 * - provides full session flow for navigation testing
 * - keeps OAuth/DeepLink/QR entry points before Supabase swap
 */
class InMemoryAuthRepository : AuthRepository {
    private val state = MutableStateFlow(AuthSessionState(isInitializing = true))

    init {
        // Simulate cold-start session restore
        state.value = AuthSessionState(isInitializing = false, isAuthenticated = false)
    }

    override fun observeSession() = state.asStateFlow()

    override suspend fun signInWithEmail(email: String, password: String): Result<Unit> = runCatching {
        delay(200)
        require(email.isNotBlank() && password.isNotBlank()) { "Email and password must not be empty" }
        state.value = AuthSessionState(
            isInitializing = false,
            isAuthenticated = true,
            user = AuthUser(
                id = "user-${email.hashCode()}",
                email = email,
                displayName = email.substringBefore("@"),
            ),
        )
    }

    override suspend fun signUpWithEmail(email: String, password: String): Result<Unit> = runCatching {
        delay(200)
        require(email.isNotBlank() && password.isNotBlank()) { "Email and password must not be empty" }
    }

    override suspend fun resetPasswordForEmail(email: String): Result<Unit> = runCatching {
        delay(200)
        require(email.isNotBlank()) { "Please enter email first" }
    }

    override suspend fun signInWithOAuth(provider: OAuthProvider): Result<Unit> = runCatching {
        provider
    }

    override suspend fun handleDeepLink(url: String): Result<Unit> = runCatching {
        if (url.contains("token=", ignoreCase = true) || url.startsWith("verlucloud://login")) {
            state.value = AuthSessionState(
                isInitializing = false,
                isAuthenticated = true,
                user = AuthUser(
                    id = "oauth-user",
                    email = "oauth@example.com",
                    displayName = "OAuth User",
                ),
            )
        } else {
            error("Invalid callback url")
        }
    }

    override suspend fun signOut(): Result<Unit> = runCatching {
        state.value = AuthSessionState(isInitializing = false, isAuthenticated = false)
    }

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun beginDesktopQrLogin(): Result<DesktopQrSession> = runCatching {
        val sessionId = Uuid.random().toString()
        DesktopQrSession(
            qrPayload = "verlusync://authorize_sso?sessionId=$sessionId",
            sessionId = sessionId,
        )
    }

    override fun observeQrApproval(sessionId: String): Flow<QrApprovalResult> = emptyFlow()

    override suspend fun signInWithQrToken(email: String, token: String): Result<Unit> = runCatching {
        delay(100)
    }
}
