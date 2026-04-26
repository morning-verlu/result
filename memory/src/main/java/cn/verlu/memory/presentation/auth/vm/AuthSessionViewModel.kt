package cn.verlu.memory.presentation.auth.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class AuthSessionState(
    val isInitializing: Boolean = true,
    val isAuthenticated: Boolean = false,
    val user: UserInfo? = null,
)

@HiltViewModel
class AuthSessionViewModel @Inject constructor(
    private val supabase: SupabaseClient,
) : ViewModel() {

    private val _state = MutableStateFlow(AuthSessionState())
    val state: StateFlow<AuthSessionState> = _state.asStateFlow()
    private var seenInitializing = false
    private var forceSignedOut = false

    fun signOut() {
        forceSignedOut = true
        _state.value = AuthSessionState(
            isInitializing = false,
            isAuthenticated = false,
            user = null,
        )
        viewModelScope.launch {
            runCatching { supabase.auth.signOut() }
        }
    }

    init {
        viewModelScope.launch {
            supabase.auth.sessionStatus.collectLatest { sessionStatus ->
                when (sessionStatus) {
                    is SessionStatus.Authenticated -> {
                        forceSignedOut = false
                        seenInitializing = false
                        _state.value = AuthSessionState(
                            isInitializing = false,
                            isAuthenticated = true,
                            user = sessionStatus.session.user,
                        )
                    }
                    SessionStatus.Initializing -> {
                        seenInitializing = true
                        _state.value = AuthSessionState(
                            isInitializing = true,
                            isAuthenticated = false,
                            user = null,
                        )
                    }
                    else -> {
                        if (forceSignedOut) {
                            forceSignedOut = false
                            seenInitializing = false
                            _state.value = AuthSessionState(
                                isInitializing = false,
                                isAuthenticated = false,
                                user = null,
                            )
                            return@collectLatest
                        }
                        if (seenInitializing) {
                            // 冷启动瞬时未登录抖动在 VM 层防抖，避免 UI/Nav 写 delay 补丁。
                            delay(500)
                        }
                        val currentUser = supabase.auth.currentUserOrNull()
                        seenInitializing = false
                        _state.value = AuthSessionState(
                            isInitializing = false,
                            isAuthenticated = currentUser != null,
                            user = currentUser,
                        )
                    }
                }
            }
        }
    }
}
