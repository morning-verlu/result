package cn.verlu.sync.presentation.auth.vm

import kotlinx.coroutines.flow.MutableStateFlow

/** 从 Talk / Doctor 深链拉起时携带「授权完成后应回到哪个 App」。 */
data class PendingSsoAuthorize(
    val sessionId: String,
    /** 目标包名，如 [cn.verlu.doctor] / [cn.verlu.talk]；null 表示不自动跳转。 */
    val returnPackage: String?,
)

object AuthEventManager {
    val showPasswordResetDialog = MutableStateFlow(false)
    val pendingSsoAuthorize = MutableStateFlow<PendingSsoAuthorize?>(null)
}
