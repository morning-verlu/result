package cn.verlu.sync.presentation.auth.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UpdatePasswordState(
    val password: String = "",
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val success: Boolean = false
)

@HiltViewModel
class UpdatePasswordViewModel @Inject constructor(
    private val supabase: SupabaseClient
) : ViewModel() {

    private val _state = MutableStateFlow(UpdatePasswordState())
    val state: StateFlow<UpdatePasswordState> = _state.asStateFlow()

    fun onPasswordChange(password: String) {
        _state.update { it.copy(password = password, error = null) }
    }

    fun submit() {
        viewModelScope.launch {
            val password = state.value.password
            if (password.length < 6) {
                _state.update { it.copy(error = "密码不能少于6位") }
                return@launch
            }

            _state.update { it.copy(isSubmitting = true, error = null) }
            runCatching {
                supabase.auth.updateUser {
                    this.password = password
                }
            }.onFailure { e ->
                _state.update {
                    it.copy(
                        error = e.message ?: "修改失败，请重试",
                        isSubmitting = false
                    )
                }
            }.onSuccess {
                _state.update { it.copy(success = true, isSubmitting = false) }
            }
        }
    }
}
