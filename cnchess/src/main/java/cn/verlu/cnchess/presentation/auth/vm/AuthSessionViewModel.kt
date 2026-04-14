package cn.verlu.cnchess.presentation.auth.vm

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

    fun signOut() {
        viewModelScope.launch {
            runCatching { supabase.auth.signOut() }
        }
    }

    init {
        viewModelScope.launch {
            var wasInitializing = true
            supabase.auth.sessionStatus.collectLatest { sessionStatus ->
                when (sessionStatus) {
                    is SessionStatus.Authenticated -> {
                        wasInitializing = false
                        _state.value = AuthSessionState(
                            isInitializing = false,
                            isAuthenticated = true,
                            user = sessionStatus.session.user,
                        )
                    }
                    SessionStatus.Initializing -> {
                        wasInitializing = true
                        _state.value = AuthSessionState(
                            isInitializing = true,
                            isAuthenticated = false,
                            user = null,
                        )
                    }
                    else -> {
                        if (wasInitializing) delay(500)
                        wasInitializing = false
                        _state.value = AuthSessionState(
                            isInitializing = false,
                            isAuthenticated = false,
                            user = null,
                        )
                    }
                }
            }
        }
    }
}
