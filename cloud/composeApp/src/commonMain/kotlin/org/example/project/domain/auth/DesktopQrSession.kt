package cn.verlu.cloud.domain.auth

/** Cloud 桌面端发起扫码登录时生成的会话信息。 */
data class DesktopQrSession(
    /** 生成的二维码内容，格式 verlusync://authorize_sso?sessionId=UUID */
    val qrPayload: String,
    val sessionId: String,
)

/** 手机端（Talk / Sync）扫码并调用 approve-login 后，桌面端轮询到的授权结果。 */
data class QrApprovalResult(
    val email: String,
    val token: String,
)
