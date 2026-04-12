package cn.verlu.cloud.data.auth

import kotlinx.coroutines.flow.Flow
import cn.verlu.cloud.domain.auth.AuthSessionState
import cn.verlu.cloud.domain.auth.OAuthProvider
import cn.verlu.cloud.domain.auth.DesktopQrSession
import cn.verlu.cloud.domain.auth.QrApprovalResult

interface AuthRepository {
    fun observeSession(): Flow<AuthSessionState>
    suspend fun signInWithEmail(email: String, password: String): Result<Unit>
    suspend fun signUpWithEmail(email: String, password: String): Result<Unit>
    suspend fun resetPasswordForEmail(email: String): Result<Unit>
    suspend fun signInWithOAuth(provider: OAuthProvider): Result<Unit>
    suspend fun handleDeepLink(url: String): Result<Unit>
    suspend fun signOut(): Result<Unit>
    /** 在 qr_login_sessions 插入一条记录，并返回含二维码载荷和 sessionId 的对象。 */
    suspend fun beginDesktopQrLogin(): Result<DesktopQrSession>
    /** 通过 Supabase Realtime 监听 qr_login_sessions，在手机端 approve-login 写入后收到授权。 */
    fun observeQrApproval(sessionId: String): Flow<QrApprovalResult>
    /** 用 approve-login Edge Function 返回的 email + OTP token 完成登录。 */
    suspend fun signInWithQrToken(email: String, token: String): Result<Unit>
}
