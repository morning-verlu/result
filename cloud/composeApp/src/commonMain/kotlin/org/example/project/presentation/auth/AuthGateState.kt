package cn.verlu.cloud.presentation.auth

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import cn.verlu.cloud.domain.auth.AuthSessionState
import cn.verlu.cloud.domain.auth.AuthUseCases
import cn.verlu.cloud.domain.auth.OAuthProvider

private const val INITIAL_UNAUTH_DEBOUNCE_MS = 300L
private const val DESKTOP_QR_TTL_MS = 2 * 60 * 1000L

enum class AuthFormMode { Login, Register }

data class AuthFormState(
    val mode: AuthFormMode = AuthFormMode.Login,
    val email: String = "",
    val password: String = "",
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val info: String? = null,
)

class AuthGateState(
    private val authUseCases: AuthUseCases,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Session state
    private val _session = MutableStateFlow(AuthSessionState())
    val session: StateFlow<AuthSessionState> = _session.asStateFlow()

    // Form state
    private val _form = MutableStateFlow(AuthFormState())
    val form: StateFlow<AuthFormState> = _form.asStateFlow()

    /** ?????????????URL ?????????? null */
    private val _desktopQrPayload = MutableStateFlow<String?>(null)
    val desktopQrPayload: StateFlow<String?> = _desktopQrPayload.asStateFlow()
    private val _desktopQrExpiresAtMs = MutableStateFlow<Long?>(null)
    val desktopQrExpiresAtMs: StateFlow<Long?> = _desktopQrExpiresAtMs.asStateFlow()

    private var qrListenJob: Job? = null

    init {
        var isFirst = true
        scope.launch {
            authUseCases.observeSession().collectLatest { incoming ->
                if (isFirst && !incoming.isInitializing && !incoming.isAuthenticated) {
                    delay(INITIAL_UNAUTH_DEBOUNCE_MS)
                }
                isFirst = false
                _session.value = incoming
                // Clear submitting flag when session change happens
                if (_form.value.isSubmitting && (incoming.isAuthenticated || !incoming.isInitializing)) {
                    _form.update { it.copy(isSubmitting = false) }
                }
            }
        }
    }

    fun setMode(mode: AuthFormMode) {
        _form.update { it.copy(mode = mode, error = null, info = null) }
    }

    fun onEmailChange(email: String) {
        _form.update { it.copy(email = email, error = null) }
    }

    fun onPasswordChange(password: String) {
        _form.update { it.copy(password = password, error = null) }
    }

    fun clearFormMessage() {
        _form.update { it.copy(error = null, info = null) }
    }

    fun submit() {
        when (_form.value.mode) {
            AuthFormMode.Login -> login()
            AuthFormMode.Register -> register()
        }
    }

    fun resetPassword() {
        val email = _form.value.email.trim()
        if (email.isEmpty()) {
            _form.update { it.copy(error = "Please enter email on previous step") }
            return
        }
        scope.launch {
            _form.update { it.copy(isSubmitting = true, error = null, info = null) }
            authUseCases.resetPassword(email)
                .onSuccess { _form.update { it.copy(info = "Reset password email sent") } }
                .onFailure { _form.update { it.copy(error = "Failed to send reset password email") } }
            _form.update { it.copy(isSubmitting = false) }
        }
    }

    fun signInOAuth(provider: OAuthProvider, onStarted: () -> Unit, onError: (String) -> Unit) {
        scope.launch {
            _form.update { it.copy(isSubmitting = true, error = null, info = null) }
            authUseCases.signInOAuth(provider)
                .onSuccess { onStarted() }
                .onFailure { e ->
                    val msg = e.message ?: "OAuth sign-in failed"
                    _form.update { it.copy(isSubmitting = false, error = msg) }
                    onError(msg)
                }
        }
    }

    fun handleDeepLink(url: String, onError: (String) -> Unit = {}) {
        scope.launch {
            authUseCases.handleDeepLink(url).onFailure { e ->
                val msg = e.message ?: "Failed to handle callback"
                // OAuth 浏览器回调可能因 flow_state 过期/丢失失败，降级为可见错误而非崩溃
                _form.update { it.copy(isSubmitting = false, error = msg) }
                onError(msg)
            }
        }
    }

    fun requestDesktopQrLogin() {
        scope.launch {
            _form.update { it.copy(isSubmitting = true, error = null, info = null) }
            authUseCases.beginDesktopQrLogin()
                .onSuccess { session ->
                    _desktopQrPayload.value = session.qrPayload
                    _desktopQrExpiresAtMs.value = kotlin.time.Clock.System.now().toEpochMilliseconds() + DESKTOP_QR_TTL_MS
                    startListeningForQrApproval(session.sessionId)
                }
                .onFailure { e ->
                    _form.update { it.copy(error = e.message ?: "??????????") }
                }
            _form.update { it.copy(isSubmitting = false) }
        }
    }

    private fun startListeningForQrApproval(sessionId: String) {
        qrListenJob?.cancel()
        qrListenJob = scope.launch {
            println("[CloudQR] Started listening for approval: sessionId=$sessionId")
            authUseCases.observeQrApproval(sessionId)
                .take(1)
                .collect { approval ->
                    println("[CloudQR] Approval received! email=${approval.email}, tokenLen=${approval.token.length}, token='${approval.token}'")
                    _form.update { it.copy(info = "QR login: verifying token...") }
                    authUseCases.signInWithQrToken(approval.email, approval.token)
                        .onSuccess {
                            println("[CloudQR] signInWithQrToken SUCCESS - waiting for session update")
                            _desktopQrPayload.value = null
                            _desktopQrExpiresAtMs.value = null
                            _form.update { it.copy(info = null) }
                        }
                        .onFailure { e ->
                            println("[CloudQR] signInWithQrToken FAILED: ${e::class.simpleName}: ${e.message}")
                            _form.update {
                                it.copy(error = "QR login failed: ${e.message}", info = null)
                            }
                        }
                    qrListenJob = null
                }
        }
    }

    fun clearDesktopQrPayload() {
        qrListenJob?.cancel()
        qrListenJob = null
        _desktopQrPayload.value = null
        _desktopQrExpiresAtMs.value = null
    }

    fun signOut(onError: (String) -> Unit = {}) {
        scope.launch {
            authUseCases.signOut().onFailure { onError(it.message ?: "Failed to sign out") }
        }
    }

    private fun login() {
        val email = _form.value.email.trim()
        val password = _form.value.password
        if (email.isEmpty() || password.isEmpty()) {
            _form.update { it.copy(error = "Please enter email and password") }
            return
        }
        scope.launch {
            _form.update { it.copy(isSubmitting = true, error = null, info = null) }
            authUseCases.signInEmail(email, password)
                .onFailure { _form.update { it.copy(error = "Login failed, please check credentials") } }
            _form.update { it.copy(isSubmitting = false) }
        }
    }

    private fun register() {
        val email = _form.value.email.trim()
        val password = _form.value.password
        if (email.isEmpty() || password.isEmpty()) {
            _form.update { it.copy(error = "Please enter email and password") }
            return
        }
        scope.launch {
            _form.update { it.copy(isSubmitting = true, error = null, info = null) }
            authUseCases.signUpEmail(email, password)
                .onSuccess { _form.update { it.copy(info = "Sign up submitted, check your email") } }
                .onFailure { _form.update { it.copy(error = "Sign up failed, email may already exist") } }
            _form.update { it.copy(isSubmitting = false) }
        }
    }
}
