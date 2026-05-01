package cn.verlu.lulu.presentation.session

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.verlu.lulu.core.auth.LuluSessionState
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

typealias SessionUiState = LuluSessionState

@HiltViewModel
class SessionViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val supabase: SupabaseClient,
) : ViewModel() {
    private val authPrefs = context.getSharedPreferences(AUTH_PREFS, Context.MODE_PRIVATE)
    private val hasLocalSessionHint = authPrefs.getBoolean(KEY_HAS_AUTHENTICATED_SESSION, false)

    private val _state = MutableStateFlow(
        SessionUiState(
            isAuthenticated = hasLocalSessionHint,
            isOfflineSession = hasLocalSessionHint,
        ),
    )
    val state: StateFlow<SessionUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val cachedSession = loadCachedSession()
            val state = _state.value
            if (cachedSession != null && state.user == null) {
                markHasAuthenticatedSession(true)
                _state.value = state.copy(
                    isAuthenticated = true,
                    user = cachedSession.user,
                    isOfflineSession = true,
                )
            } else if (cachedSession == null && !state.isAuthenticated) {
                markHasAuthenticatedSession(false)
            }
        }

        viewModelScope.launch {
            supabase.auth.sessionStatus.collectLatest { status ->
                when (status) {
                    is SessionStatus.Authenticated -> {
                        markHasAuthenticatedSession(true)
                        _state.value = SessionUiState(
                            isInitializing = false,
                            isAuthenticated = true,
                            user = status.session.user,
                            isOfflineSession = false,
                        )
                    }

                    SessionStatus.Initializing -> {
                        // Keep rendering the locally chosen route while Supabase restores/refreshes.
                    }

                    is SessionStatus.RefreshFailure -> {
                        val cachedSession = loadCachedSession()
                        if (cachedSession != null) {
                            markHasAuthenticatedSession(true)
                            _state.value = SessionUiState(
                                isInitializing = false,
                                isAuthenticated = true,
                                user = cachedSession.user,
                                isOfflineSession = true,
                            )
                        } else if (_state.value.isAuthenticated) {
                            _state.value = _state.value.copy(isOfflineSession = true)
                        }
                    }

                    is SessionStatus.NotAuthenticated -> {
                        val cachedSession = if (status.isSignOut) null else loadCachedSession()
                        if (cachedSession != null) {
                            markHasAuthenticatedSession(true)
                            _state.value = SessionUiState(
                                isInitializing = false,
                                isAuthenticated = true,
                                user = cachedSession.user,
                                isOfflineSession = true,
                            )
                            return@collectLatest
                        }
                        val canUseOfflineSession =
                            !status.isSignOut &&
                                authPrefs.getBoolean(KEY_HAS_AUTHENTICATED_SESSION, false)
                        if (canUseOfflineSession) {
                            _state.value = SessionUiState(
                                isInitializing = false,
                                isAuthenticated = true,
                                user = null,
                                isOfflineSession = true,
                            )
                            return@collectLatest
                        }
                        markHasAuthenticatedSession(false)
                        _state.value = SessionUiState(
                            isInitializing = false,
                            isAuthenticated = false,
                            user = null,
                        )
                    }
                }
            }
        }
    }

    private suspend fun loadCachedSession(): UserSession? =
        runCatching { supabase.auth.sessionManager.loadSession() }.getOrNull()

    fun signOut() {
        viewModelScope.launch {
            markHasAuthenticatedSession(false)
            _state.value = SessionUiState()
            runCatching { supabase.auth.signOut() }
        }
    }

    private fun markHasAuthenticatedSession(value: Boolean) {
        authPrefs.edit().putBoolean(KEY_HAS_AUTHENTICATED_SESSION, value).apply()
    }

    private companion object {
        private const val AUTH_PREFS = "lulu_auth_session"
        private const val KEY_HAS_AUTHENTICATED_SESSION = "has_authenticated_session"
    }
}
