package cn.verlu.sync.presentation.auth.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import javax.inject.Inject

@Serializable
data class QrLoginRequest(val sessionId: String)

@HiltViewModel
class QrLoginViewModel @Inject constructor(
    private val supabase: SupabaseClient
) : ViewModel() {

    private val _uiState = MutableStateFlow<QrLoginState>(QrLoginState.Scanning)
    val uiState: StateFlow<QrLoginState> = _uiState.asStateFlow()

    fun setSessionId(sessionId: String) {
        _uiState.value = QrLoginState.Confirming(sessionId)
    }

    fun onScanResult(sessionId: String) {
        if (_uiState.value is QrLoginState.Scanning) {
            _uiState.value = QrLoginState.Confirming(sessionId)
        }
    }

    private var approveJob: kotlinx.coroutines.Job? = null

    /** 仅消费一次：避免 Success 界面里 LaunchedEffect 在页面再次进入时重复拉起 Talk。 */
    private var pendingSuccessAutoNav = false

    fun approveLogin(sessionId: String) {
        if (approveJob?.isActive == true) return

        _uiState.value = QrLoginState.Loading
        approveJob = viewModelScope.launch {
            try {
                supabase.functions.invoke("approve-login", body = QrLoginRequest(sessionId))
                pendingSuccessAutoNav = true
                _uiState.value = QrLoginState.Success
            } catch (e: Exception) {
                _uiState.value = QrLoginState.Error(e.message ?: "Failed to approve login")
            }
        }
    }

    /**
     * @return 若为 true，调用方应执行打开 Talk / pop 等一次；随后置为已消费。
     */
    fun consumeSuccessAutoNavIfNeeded(): Boolean {
        if (!pendingSuccessAutoNav) return false
        pendingSuccessAutoNav = false
        return true
    }

    fun cancel() {
        approveJob?.cancel()
        approveJob = null
        pendingSuccessAutoNav = false
        _uiState.value = QrLoginState.Scanning
    }
}

sealed class QrLoginState {
    object Scanning : QrLoginState()
    data class Confirming(val sessionId: String) : QrLoginState()
    object Loading : QrLoginState()
    object Success : QrLoginState()
    data class Error(val message: String) : QrLoginState()
}
