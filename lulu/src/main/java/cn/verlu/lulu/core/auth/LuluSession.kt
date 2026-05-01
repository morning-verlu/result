package cn.verlu.lulu.core.auth

import androidx.compose.runtime.compositionLocalOf
import io.github.jan.supabase.auth.user.UserInfo

data class LuluSessionState(
    val isInitializing: Boolean = false,
    val isAuthenticated: Boolean = false,
    val user: UserInfo? = null,
    val isOfflineSession: Boolean = false,
)

val LocalLuluSession = compositionLocalOf<LuluSessionState> {
    error("No Lulu session provided")
}
