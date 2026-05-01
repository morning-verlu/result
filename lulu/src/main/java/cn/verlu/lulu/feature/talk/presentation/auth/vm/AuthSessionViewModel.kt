package cn.verlu.lulu.feature.talk.presentation.auth.vm

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class AuthSessionState(
    val isInitializing: Boolean = false,
    val isAuthenticated: Boolean = false,
    val user: UserInfo? = null
)

@HiltViewModel
class AuthSessionViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val supabase: SupabaseClient
) : ViewModel() {

    private val authPrefs = context.getSharedPreferences(AUTH_PREFS, Context.MODE_PRIVATE)
    private val hasLocalSessionHint = authPrefs.getBoolean(KEY_HAS_AUTHENTICATED_SESSION, false)

    private val _state = MutableStateFlow(
        AuthSessionState(
            isAuthenticated = hasLocalSessionHint,
        ),
    )
    val state: StateFlow<AuthSessionState> = _state.asStateFlow()

    fun signOut() {
        viewModelScope.launch {
            markHasAuthenticatedSession(false)
            _state.value = AuthSessionState()
            runCatching { supabase.auth.signOut() }
        }
    }

    init {
        viewModelScope.launch {
            val cachedSession = loadCachedSession()
            val state = _state.value
            if (cachedSession != null && state.user == null) {
                markHasAuthenticatedSession(true)
                _state.value = state.copy(
                    isAuthenticated = true,
                    user = cachedSession.user,
                )
            } else if (cachedSession == null && !state.isAuthenticated) {
                markHasAuthenticatedSession(false)
            }
        }

        viewModelScope.launch {
            supabase.auth.sessionStatus.collectLatest { sessionStatus ->
                when (sessionStatus) {
                    is SessionStatus.Authenticated -> {
                        markHasAuthenticatedSession(true)
                        _state.value = AuthSessionState(
                            isInitializing = false,
                            isAuthenticated = true,
                            user = sessionStatus.session.user
                        )
                    }
                    SessionStatus.Initializing -> {
                        // Keep the locally chosen route visible while Supabase restores/refreshes.
                    }
                    is SessionStatus.RefreshFailure -> {
                        val cachedSession = loadCachedSession()
                        if (cachedSession != null) {
                            markHasAuthenticatedSession(true)
                            _state.value = AuthSessionState(
                                isInitializing = false,
                                isAuthenticated = true,
                                user = cachedSession.user,
                            )
                        }
                    }
                    is SessionStatus.NotAuthenticated -> {
                        val cachedSession = if (sessionStatus.isSignOut) null else loadCachedSession()
                        if (cachedSession != null) {
                            markHasAuthenticatedSession(true)
                            _state.value = AuthSessionState(
                                isInitializing = false,
                                isAuthenticated = true,
                                user = cachedSession.user,
                            )
                            return@collectLatest
                        }
                        markHasAuthenticatedSession(false)
                        _state.value = AuthSessionState()
                    }
                }
            }
        }
    }

    private suspend fun loadCachedSession(): UserSession? =
        runCatching { supabase.auth.sessionManager.loadSession() }.getOrNull()

    private fun markHasAuthenticatedSession(value: Boolean) {
        authPrefs.edit().putBoolean(KEY_HAS_AUTHENTICATED_SESSION, value).apply()
    }

    private companion object {
        private const val AUTH_PREFS = "talk_auth_session"
        private const val KEY_HAS_AUTHENTICATED_SESSION = "has_authenticated_session"
    }
}
