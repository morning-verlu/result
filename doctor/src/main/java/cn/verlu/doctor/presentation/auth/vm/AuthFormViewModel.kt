package cn.verlu.doctor.presentation.auth.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Github
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

/** 与 AppModule Auth scheme/host、AndroidManifest 一致；注册确认邮件、 magic link 等会回到 Doctor。 */
private const val EMAIL_AUTH_REDIRECT = "doctorapp://login"
private const val QR_SESSION_TTL_MS = 2 * 60 * 1000L

enum class AuthMode {
    Login,
    Register,
}

@Serializable
data class QrSession(
    val session_id: String,
    val status: String = "pending",
    val email: String? = null,
    val login_token: String? = null,
    val expires_at: String? = null,
)

data class AuthFormState(
    val mode: AuthMode = AuthMode.Login,
    val email: String = "",
    val password: String = "",
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val info: String? = null,
    val qrSessionId: String? = null,
    val qrExpiresAtMs: Long? = null,
)

@HiltViewModel
class AuthFormViewModel @Inject constructor(
    private val supabase: SupabaseClient,
) : ViewModel() {

    private val _state = MutableStateFlow(AuthFormState())
    val state: StateFlow<AuthFormState> = _state.asStateFlow()

    private var qrJob: Job? = null

    init {
        viewModelScope.launch {
            supabase.auth.sessionStatus.collect { status ->
                if (status is SessionStatus.Authenticated) {
                    _state.update { s -> if (s.isSubmitting) s.copy(isSubmitting = false) else s }
                }
            }
        }
    }

    fun setMode(mode: AuthMode) {
        qrJob?.cancel()
        _state.update {
            it.copy(
                mode = mode,
                error = null,
                info = null,
                qrSessionId = null,
            )
        }
    }

    fun onEmailChange(email: String) {
        _state.update { it.copy(email = email, error = null, info = null) }
    }

    fun onPasswordChange(password: String) {
        _state.update { it.copy(password = password, error = null, info = null) }
    }

    fun clearMessage() {
        _state.update { it.copy(error = null, info = null) }
    }

    fun setError(error: String?) {
        _state.update { it.copy(error = error) }
    }

    /** 点击 Sync 授权：创建会话并订阅 Realtime，随后由 UI 拉起本机 Sync。 */
    fun beginSyncQrLogin() {
        qrJob?.cancel()
        _state.update { it.copy(error = null, info = null, qrSessionId = null, qrExpiresAtMs = null) }
        startQrLogin()
    }

    fun cancelSyncQrLogin() {
        qrJob?.cancel()
        qrJob = null
        _state.update { it.copy(qrSessionId = null, qrExpiresAtMs = null) }
    }

    fun submit() {
        when (state.value.mode) {
            AuthMode.Login -> login()
            AuthMode.Register -> register()
        }
    }

    fun resetPassword() {
        viewModelScope.launch {
            val email = state.value.email.trim()
            if (email.isEmpty()) {
                _state.update { it.copy(error = "请先返回上一页填写邮箱") }
                return@launch
            }
            _state.update { it.copy(isSubmitting = true, error = null, info = null) }
            runCatching {
                supabase.auth.resetPasswordForEmail(email, "$EMAIL_AUTH_REDIRECT?type=recovery")
            }.onFailure {
                _state.update { it.copy(error = "发送重置密码邮件失败，请稍后重试") }
            }.onSuccess {
                _state.update { it.copy(info = "重置密码邮件已发送，请前往邮箱操作。") }
            }.also {
                _state.update { it.copy(isSubmitting = false) }
            }
        }
    }

    fun signInWithGithub() {
        oauthSignIn(providerName = "github") {
            supabase.auth.signInWith(Github, redirectUrl = EMAIL_AUTH_REDIRECT)
        }
    }

    fun signInWithGoogle() {
        oauthSignIn(providerName = "google") {
            supabase.auth.signInWith(Google, redirectUrl = EMAIL_AUTH_REDIRECT)
        }
    }

    private fun oauthSignIn(
        providerName: String,
        block: suspend () -> Unit,
    ) {
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, error = null, info = null) }
            runCatching { block() }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            error = e.message ?: "第三方登录失败（$providerName）",
                            isSubmitting = false,
                        )
                    }
                }
        }
    }

    private fun login() {
        viewModelScope.launch {
            val email = state.value.email.trim()
            val password = state.value.password
            if (email.isEmpty() || password.isEmpty()) {
                _state.update { it.copy(error = "请填写邮箱和密码") }
                return@launch
            }

            _state.update { it.copy(isSubmitting = true, error = null, info = null) }
            runCatching {
                supabase.auth.signInWith(Email, redirectUrl = EMAIL_AUTH_REDIRECT) {
                    this.email = email
                    this.password = password
                }
            }.onFailure {
                _state.update { it.copy(error = "登录失败，请检查邮箱和密码是否正确") }
            }.onSuccess {
                _state.update { it.copy(info = null, error = null) }
            }.also {
                _state.update { it.copy(isSubmitting = false) }
            }
        }
    }

    private fun register() {
        viewModelScope.launch {
            val email = state.value.email.trim()
            val password = state.value.password
            if (email.isEmpty() || password.isEmpty()) {
                _state.update { it.copy(error = "请填写邮箱和密码") }
                return@launch
            }

            _state.update { it.copy(isSubmitting = true, error = null, info = null) }
            runCatching {
                supabase.auth.signUpWith(Email, redirectUrl = EMAIL_AUTH_REDIRECT) {
                    this.email = email
                    this.password = password
                }
            }.onFailure {
                _state.update { it.copy(error = "注册失败，该邮箱可能已被注册") }
            }.onSuccess {
                _state.update {
                    it.copy(info = "注册已提交。请前往邮箱确认后再登录。")
                }
            }.also {
                _state.update { it.copy(isSubmitting = false) }
            }
        }
    }

    private fun startQrLogin() {
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true) }
            val sessionId = UUID.randomUUID().toString()
            val expiresAtMs = System.currentTimeMillis() + QR_SESSION_TTL_MS
            runCatching {
                supabase.postgrest["qr_login_sessions"].insert(
                    mapOf(
                        "session_id" to sessionId,
                        "expires_at" to Instant.ofEpochMilli(expiresAtMs).toString(),
                    )
                )
            }.onFailure { e ->
                _state.update {
                    it.copy(
                        error = "创建登录会话失败: ${e.message}",
                        isSubmitting = false,
                    )
                }
            }.onSuccess {
                _state.update {
                    it.copy(
                        qrSessionId = sessionId,
                        qrExpiresAtMs = expiresAtMs,
                        isSubmitting = false,
                    )
                }
                listenToQrSession(sessionId)
            }
        }
    }

    private fun listenToQrSession(sessionId: String) {
        val channel = supabase.realtime.channel("qr_auth_$sessionId")

        val realtimeJob = channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
            table = "qr_login_sessions"
        }.onEach { change ->
            runCatching {
                val session = change.decodeRecord<QrSession>()
                handleQrSessionState(session, sessionId)
            }
        }.launchIn(viewModelScope)

        qrJob = realtimeJob

        viewModelScope.launch {
            runCatching { channel.subscribe() }
                .onFailure { _ ->
                    realtimeJob.cancel()
                    qrJob = startPollingFallback(sessionId)
                }
        }
    }

    private fun startPollingFallback(sessionId: String): Job =
        viewModelScope.launch {
            while (true) {
                delay(3_000)
                if (state.value.qrSessionId != sessionId) break
                runCatching {
                    supabase.postgrest["qr_login_sessions"].select {
                        filter { eq("session_id", sessionId) }
                    }.decodeSingle<QrSession>()
                }.onSuccess { session ->
                    handleQrSessionState(session, sessionId)
                }
            }
        }

    private fun handleQrSessionState(session: QrSession, sessionId: String) {
        if (session.session_id != sessionId) return

        if (session.status == "expired" || isQrSessionExpired(session)) {
            _state.update {
                it.copy(
                    error = "授权会话已过期，请重试",
                    qrSessionId = null,
                    qrExpiresAtMs = null,
                )
            }
            qrJob?.cancel()
            return
        }

        if (session.status == "approved") {
            val email = session.email
            val token = session.login_token
            if (email != null && token != null) {
                loginWithToken(email, token)
                qrJob?.cancel()
            }
        }
    }

    fun checkQrSessionStatus() {
        val sessionId = state.value.qrSessionId ?: return
        viewModelScope.launch {
            runCatching {
                supabase.postgrest["qr_login_sessions"].select {
                    filter { eq("session_id", sessionId) }
                }.decodeSingle<QrSession>()
            }.onSuccess { session ->
                handleQrSessionState(session, sessionId)
            }
        }
    }

    private fun loginWithToken(email: String, token: String) {
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, info = "正在授权登录…") }
            runCatching<Unit> {
                val type = if (token.length == 6) OtpType.Email.EMAIL else OtpType.Email.MAGIC_LINK
                supabase.auth.verifyEmailOtp(
                    type = type,
                    email = email,
                    token = token,
                )
            }.onFailure { e ->
                _state.update {
                    it.copy(
                        error = "登录令牌验证失败: ${e.message}",
                        isSubmitting = false,
                    )
                }
            }.onSuccess {
                _state.update {
                    it.copy(
                        isSubmitting = false,
                        info = "登录成功",
                    )
                }
            }
        }
    }

    private fun isQrSessionExpired(session: QrSession): Boolean {
        val expiresAt = session.expires_at ?: return false
        return runCatching { Instant.parse(expiresAt).toEpochMilli() <= System.currentTimeMillis() }
            .getOrDefault(false)
    }

    override fun onCleared() {
        qrJob?.cancel()
        super.onCleared()
    }
}
