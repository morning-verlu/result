package cn.verlu.cloud.domain.auth

enum class OAuthProvider {
    GitHub,
    Google,
}

data class AuthUser(
    val id: String,
    val email: String?,
    val displayName: String?,
)

data class AuthSessionState(
    val isInitializing: Boolean = true,
    val isAuthenticated: Boolean = false,
    val user: AuthUser? = null,
    val error: String? = null,
)

sealed interface AuthIntent {
    data class SignInWithEmail(val email: String, val password: String) : AuthIntent
    data class SignInWithOAuth(val provider: OAuthProvider) : AuthIntent
    data class HandleDeepLink(val url: String) : AuthIntent
    data object SignOut : AuthIntent
}
