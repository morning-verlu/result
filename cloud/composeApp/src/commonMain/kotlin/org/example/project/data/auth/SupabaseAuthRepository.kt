package cn.verlu.cloud.data.auth

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import cn.verlu.cloud.data.remote.SupabaseConfig
import cn.verlu.cloud.domain.auth.AuthSessionState
import cn.verlu.cloud.domain.auth.AuthUser
import cn.verlu.cloud.domain.auth.DesktopQrSession
import cn.verlu.cloud.domain.auth.OAuthProvider
import cn.verlu.cloud.domain.auth.QrApprovalResult
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
import io.ktor.http.Url
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class SupabaseAuthRepository(
    private val supabase: SupabaseClient,
) : AuthRepository {

    override fun observeSession(): Flow<AuthSessionState> =
        supabase.auth.sessionStatus.map { status ->
            when (status) {
                SessionStatus.Initializing ->
                    AuthSessionState(
                        isInitializing = true,
                        isAuthenticated = false,
                    )

                is SessionStatus.Authenticated ->
                    AuthSessionState(
                        isInitializing = false,
                        isAuthenticated = true,
                        user = AuthUser(
                            id = status.session.user?.id.orEmpty(),
                            email = status.session.user?.email,
                            displayName = status.session.user?.userMetadata?.get("name")?.toString()?.trim('"'),
                        ),
                    )

                is SessionStatus.NotAuthenticated ->
                    AuthSessionState(
                        isInitializing = false,
                        isAuthenticated = false,
                    )

                else ->
                    AuthSessionState(
                        isInitializing = false,
                        isAuthenticated = false,
                    )
            }
        }

    override suspend fun signInWithEmail(email: String, password: String): Result<Unit> = runCatching {
        val normalized = email.trim()
        require(normalized.isNotBlank() && password.isNotBlank()) { "Email and password must not be empty" }
        supabase.auth.signInWith(Email, redirectUrl = SupabaseConfig.REDIRECT_URI) {
            this.email = normalized
            this.password = password
        }
    }

    override suspend fun signUpWithEmail(email: String, password: String): Result<Unit> = runCatching {
        val normalized = email.trim()
        require(normalized.isNotBlank() && password.isNotBlank()) { "Email and password must not be empty" }
        supabase.auth.signUpWith(Email, redirectUrl = SupabaseConfig.REDIRECT_URI) {
            this.email = normalized
            this.password = password
        }
    }

    override suspend fun resetPasswordForEmail(email: String): Result<Unit> = runCatching {
        val normalized = email.trim()
        require(normalized.isNotBlank()) { "Please enter email first" }
        supabase.auth.resetPasswordForEmail(normalized, "${SupabaseConfig.REDIRECT_URI}?type=recovery")
    }

    override suspend fun signInWithOAuth(provider: OAuthProvider): Result<Unit> = runCatching {
        when (provider) {
            OAuthProvider.GitHub -> {
                supabase.auth.signInWith(Github, redirectUrl = SupabaseConfig.REDIRECT_URI)
            }
            OAuthProvider.Google -> {
                supabase.auth.signInWith(Google, redirectUrl = SupabaseConfig.REDIRECT_URI)
            }
        }
    }

    override suspend fun handleDeepLink(url: String): Result<Unit> = runCatching {
        require(url.startsWith(SupabaseConfig.REDIRECT_URI)) { "Invalid callback url" }
        // Desktop extracts code and exchanges session; Android imports via handleDeeplinks
        val code = Url(url).parameters["code"]
        if (!code.isNullOrBlank()) {
            supabase.auth.exchangeCodeForSession(code)
        }
    }

    override suspend fun signOut(): Result<Unit> = runCatching {
        supabase.auth.signOut()
    }

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun beginDesktopQrLogin(): Result<DesktopQrSession> = runCatching {
        val sessionId = Uuid.random().toString()
        println("[CloudQR] Inserting session: $sessionId")
        try {
            supabase.postgrest["qr_login_sessions"].insert(mapOf("session_id" to sessionId))
            println("[CloudQR] Insert OK")
        } catch (e: Exception) {
            println("[CloudQR] Insert FAILED: ${e::class.simpleName}: ${e.message}")
            throw e
        }
        val qrPayload = "verlusync://authorize_sso?sessionId=$sessionId"
        DesktopQrSession(qrPayload = qrPayload, sessionId = sessionId)
    }

    @Serializable
    private data class QrSessionRow(
        val session_id: String,
        val status: String? = null,
        val email: String? = null,
        val login_token: String? = null,
    )

    override fun observeQrApproval(sessionId: String): Flow<QrApprovalResult> = callbackFlow {
        val channel = supabase.realtime.channel("qr_auth_$sessionId")

        // 一次性兜底检查（非轮询）：防止在订阅前已被批准。
        runCatching {
            supabase.postgrest["qr_login_sessions"]
                .select { filter { eq("session_id", sessionId) } }
                .decodeList<JsonObject>()
                .firstOrNull()
        }.onSuccess { row ->
            val status = row?.get("status")?.jsonPrimitive?.contentOrNull
            if (status == "approved") {
                val email = row["email"]?.jsonPrimitive?.contentOrNull
                val token = row["login_token"]?.jsonPrimitive?.contentOrNull
                if (!email.isNullOrBlank() && !token.isNullOrBlank()) {
                    trySend(QrApprovalResult(email = email, token = token))
                    close()
                    return@callbackFlow
                }
            }
        }

        val updatesJob = channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
            table = "qr_login_sessions"
        }.onEach { change ->
            runCatching { change.decodeRecord<QrSessionRow>() }
                .onSuccess { row ->
                    if (row.session_id != sessionId) return@onSuccess
                    if (row.status != "approved") return@onSuccess
                    val email = row.email
                    val token = row.login_token
                    if (!email.isNullOrBlank() && !token.isNullOrBlank()) {
                        trySend(QrApprovalResult(email = email, token = token))
                        close()
                    }
                }
                .onFailure { e ->
                    println("[CloudQR] Realtime decode failed: ${e::class.simpleName}: ${e.message}")
                }
        }.launchIn(this)

        launch {
            try {
                channel.subscribe()
            } catch (e: Exception) {
                close(IllegalStateException("Realtime subscribe failed: ${e.message}", e))
            }
        }

        awaitClose {
            updatesJob.cancel()
            launch { runCatching { supabase.realtime.removeChannel(channel) } }
        }
    }.take(1)

    override suspend fun signInWithQrToken(email: String, token: String): Result<Unit> = runCatching {
        val type = if (token.length == 6) OtpType.Email.EMAIL else OtpType.Email.MAGIC_LINK
        println("[CloudQR] verifyEmailOtp ? type=$type email=$email tokenLen=${token.length}")
        try {
            supabase.auth.verifyEmailOtp(type = type, email = email, token = token)
            println("[CloudQR] verifyEmailOtp returned (no exception)")
        } catch (e: Exception) {
            println("[CloudQR] verifyEmailOtp threw: ${e::class.qualifiedName}: ${e.message}")
            throw e
        }
    }
}
