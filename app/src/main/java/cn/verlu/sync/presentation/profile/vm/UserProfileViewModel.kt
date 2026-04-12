package cn.verlu.sync.presentation.profile.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UserProfileState(
    val isLoggingOut: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class UserProfileViewModel @Inject constructor(
    private val supabase: SupabaseClient
) : ViewModel() {

    private val _state = MutableStateFlow(UserProfileState())
    val state: StateFlow<UserProfileState> = _state.asStateFlow()

    fun logOut() {
        if (_state.value.isLoggingOut) return
        _state.value = _state.value.copy(isLoggingOut = true, error = null)
        viewModelScope.launch {
            try {
                supabase.auth.signOut()
                _state.value = _state.value.copy(isLoggingOut = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoggingOut = false,
                    error = e.localizedMessage ?: "退出登录失败"
                )
            }
        }
    }
}
