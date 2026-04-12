package cn.verlu.cloud.domain.auth

import kotlinx.coroutines.flow.Flow
import cn.verlu.cloud.data.auth.AuthRepository

class ObserveSession(private val repository: AuthRepository) {
    operator fun invoke(): Flow<AuthSessionState> = repository.observeSession()
}

class SignInEmail(private val repository: AuthRepository) {
    suspend operator fun invoke(email: String, password: String): Result<Unit> =
        repository.signInWithEmail(email, password)
}

class SignUpEmail(private val repository: AuthRepository) {
    suspend operator fun invoke(email: String, password: String): Result<Unit> =
        repository.signUpWithEmail(email, password)
}

class ResetPassword(private val repository: AuthRepository) {
    suspend operator fun invoke(email: String): Result<Unit> =
        repository.resetPasswordForEmail(email)
}

class SignInOAuth(private val repository: AuthRepository) {
    suspend operator fun invoke(provider: OAuthProvider): Result<Unit> =
        repository.signInWithOAuth(provider)
}

class HandleDeepLink(private val repository: AuthRepository) {
    suspend operator fun invoke(url: String): Result<Unit> = repository.handleDeepLink(url)
}

class BeginDesktopQrLogin(private val repository: AuthRepository) {
    suspend operator fun invoke(): Result<DesktopQrSession> = repository.beginDesktopQrLogin()
}

class ObserveQrApproval(private val repository: AuthRepository) {
    operator fun invoke(sessionId: String): Flow<QrApprovalResult> =
        repository.observeQrApproval(sessionId)
}

class SignInWithQrToken(private val repository: AuthRepository) {
    suspend operator fun invoke(email: String, token: String): Result<Unit> =
        repository.signInWithQrToken(email, token)
}

class SignOut(private val repository: AuthRepository) {
    suspend operator fun invoke(): Result<Unit> = repository.signOut()
}

data class AuthUseCases(
    val observeSession: ObserveSession,
    val signInEmail: SignInEmail,
    val signUpEmail: SignUpEmail,
    val resetPassword: ResetPassword,
    val signInOAuth: SignInOAuth,
    val handleDeepLink: HandleDeepLink,
    val beginDesktopQrLogin: BeginDesktopQrLogin,
    val observeQrApproval: ObserveQrApproval,
    val signInWithQrToken: SignInWithQrToken,
    val signOut: SignOut,
)
