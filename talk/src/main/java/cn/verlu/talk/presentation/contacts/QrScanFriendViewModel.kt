package cn.verlu.talk.presentation.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.verlu.talk.data.repository.FriendRepository
import cn.verlu.talk.data.repository.ProfileRepository
import cn.verlu.talk.domain.model.Profile
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import javax.inject.Inject

data class QrScanFriendState(
    val scannedProfile: Profile? = null,
    val isLookingUp: Boolean = false,
    val isSending: Boolean = false,
    val error: String? = null,
    val success: Boolean = false,
    val successMessage: String? = null,
    /** Cloud 桌面端扫码授权：待确认的 sessionId */
    val cloudLoginSessionId: String? = null,
    val isApprovingCloud: Boolean = false,
    val cloudLoginApproved: Boolean = false,
    /**
     * Cloud 授权专用错误，显示在 AlertDialog 内部，避免被 Dialog 遮挡 snackbar 的问题。
     * 与通用 [error] 字段互相独立。
     */
    val cloudLoginError: String? = null,
)

@Serializable
private data class CloudLoginRequest(val sessionId: String)

@HiltViewModel
class QrScanFriendViewModel @Inject constructor(
    private val supabase: SupabaseClient,
    private val profileRepository: ProfileRepository,
    private val friendRepository: FriendRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(QrScanFriendState())
    val state: StateFlow<QrScanFriendState> = _state.asStateFlow()

    /** 扫到二维码后：查资料，暂停摄像头，展示底部确认卡片。 */
    fun onQrScanned(uid: String) {
        val s = _state.value
        if (s.isLookingUp || s.scannedProfile != null || s.success) return
        viewModelScope.launch {
            _state.update { it.copy(isLookingUp = true, error = null) }
            val profile = runCatching { profileRepository.getProfile(uid) }.getOrElse { e ->
                _state.update { it.copy(isLookingUp = false, error = e.message) }
                return@launch
            }
            if (profile == null) {
                _state.update { it.copy(isLookingUp = false, error = "未找到该用户") }
            } else {
                _state.update { it.copy(isLookingUp = false, scannedProfile = profile) }
            }
        }
    }

    /** 用户点击底部卡片的「发送好友申请」按钮。 */
    fun sendRequest() {
        val uid = _state.value.scannedProfile?.id ?: return
        viewModelScope.launch {
            _state.update { it.copy(isSending = true, error = null) }
            runCatching { friendRepository.sendFriendRequest(uid) }
                .onSuccess {
                    _state.update { it.copy(isSending = false, success = true, successMessage = "好友申请已发送") }
                }
                .onFailure { e ->
                    val errMsg = e.message.orEmpty()
                    when {
                        // 对方已向我发了申请 → 提示去接受，但也关闭 sheet
                        errMsg.contains("reverse_request_exists", ignoreCase = true) -> {
                            _state.update {
                                it.copy(
                                    isSending = false,
                                    success = true,
                                    successMessage = "对方已向你发送了好友申请，请在「新的朋友」中查看并同意",
                                )
                            }
                        }
                        // 已是好友或已发送过申请 → 关闭 sheet，友好提示
                        errMsg.contains("unique", ignoreCase = true) ||
                            errMsg.contains("duplicate", ignoreCase = true) ||
                            errMsg.contains("already", ignoreCase = true) -> {
                            _state.update {
                                it.copy(
                                    isSending = false,
                                    success = true,
                                    successMessage = "已是好友或申请已发送",
                                )
                            }
                        }
                        // 真正的错误 → 保留 sheet，展示错误
                        else -> {
                            _state.update { it.copy(isSending = false, error = "发送失败：$errMsg") }
                        }
                    }
                }
        }
    }

    /** 关闭底部 sheet，重置 profile（允许再次扫码）。 */
    fun dismissProfile() {
        _state.update { it.copy(scannedProfile = null, error = null) }
    }

    /** Home 层消费成功状态后重置，防止重进页面二次触发。 */
    fun clearSuccess() {
        _state.update { it.copy(success = false, successMessage = null) }
    }

    /** Home 层消费错误提示后重置。 */
    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    /** 重置所有状态，供重新扫码使用。 */
    fun reset() {
        _state.value = QrScanFriendState()
    }

    // ── Cloud 桌面端扫码登录授权 ──────────────────────────────────────────

    /** 扫到 verlusync://authorize_sso?sessionId=XXX 时调用。 */
    fun onCloudLoginQrScanned(sessionId: String) {
        if (_state.value.cloudLoginSessionId != null || _state.value.isLookingUp) return
        _state.update { it.copy(cloudLoginSessionId = sessionId) }
    }

    /** 用户确认授权，调用 approve-login Edge Function。 */
    fun approveCloudLogin() {
        val sessionId = _state.value.cloudLoginSessionId ?: return
        viewModelScope.launch {
            _state.update { it.copy(isApprovingCloud = true, cloudLoginError = null) }
            runCatching {
                supabase.functions.invoke("approve-login", body = CloudLoginRequest(sessionId))
            }.onSuccess {
                _state.update {
                    it.copy(
                        isApprovingCloud = false,
                        cloudLoginApproved = true,
                        cloudLoginSessionId = null,
                        cloudLoginError = null,
                    )
                }
            }.onFailure { e ->
                // 错误存入专用字段，在 AlertDialog 内部展示，避免被 Dialog 层遮住 snackbar
                val msg = when {
                    e.message?.contains("expired", ignoreCase = true) == true -> "会话已过期，请重新扫码"
                    e.message?.contains("not found", ignoreCase = true) == true -> "会话不存在，请重新扫码"
                    e.message?.contains("pending", ignoreCase = true) == true -> "会话状态异常，请重新扫码"
                    else -> "授权失败：${e.message}"
                }
                _state.update { it.copy(isApprovingCloud = false, cloudLoginError = msg) }
            }
        }
    }

    fun dismissCloudLogin() {
        _state.update { it.copy(cloudLoginSessionId = null, isApprovingCloud = false, cloudLoginError = null) }
    }

    fun clearCloudLoginApproved() {
        _state.update { it.copy(cloudLoginApproved = false) }
    }
}
