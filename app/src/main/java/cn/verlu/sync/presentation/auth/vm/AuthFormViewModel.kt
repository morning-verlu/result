package cn.verlu.sync.presentation.auth.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Github
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 与 AppModule Auth scheme/host、AndroidManifest 一致；注册确认邮件等会回到 Sync。 */
private const val EMAIL_AUTH_REDIRECT = "verlusync://login"

enum class AuthMode {
    Login,
    Register
}

data class AuthFormState(
    val mode: AuthMode = AuthMode.Login,
    val email: String = "",
    val password: String = "",
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val info: String? = null,
)

@HiltViewModel
class AuthFormViewModel @Inject constructor(
    private val supabase: SupabaseClient
) : ViewModel() {

    private val _state = MutableStateFlow(AuthFormState())
    val state: StateFlow<AuthFormState> = _state.asStateFlow()

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
        _state.update { it.copy(mode = mode, error = null, info = null) }
    }

    fun clearMessage() {
        _state.update { it.copy(error = null, info = null) }
    }

    fun onEmailChange(email: String) {
        _state.update { it.copy(email = email, error = null, info = null) }
    }

    fun onPasswordChange(password: String) {
        _state.update { it.copy(password = password, error = null, info = null) }
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
        block: suspend () -> Unit
    ) {
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, error = null, info = null) }
            runCatching { block() }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            error = e.message ?: "第三方登录失败（$providerName）",
                            isSubmitting = false
                        )
                    }
                }
            // OAuth 成功：`signInWith` 返回后会话可能仍在恢复；保持 isSubmitting 直至 SessionStatus.Authenticated
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
                // sessionStatus 会触发 SyncNavApp 自动跳转
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
                    it.copy(
                        info = "注册已提交。请前往邮箱确认后再登录。"
                    )
                }
            }.also {
                _state.update { it.copy(isSubmitting = false) }
            }
        }
    }
}

