package cn.verlu.sync

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.handleDeeplinks
import javax.inject.Inject
import cn.verlu.sync.presentation.navigation.SyncNavApp
import cn.verlu.sync.presentation.auth.vm.AuthEventManager
import cn.verlu.sync.presentation.auth.vm.PendingSsoAuthorize
import cn.verlu.sync.presentation.auth.vm.AuthSessionViewModel
import cn.verlu.sync.presentation.theme.SyncTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var supabase: SupabaseClient

    private val authSessionVm: AuthSessionViewModel by viewModels()

    private var timeoutReached = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            delay(3000)
            timeoutReached = true
        }

        splashScreen.setKeepOnScreenCondition {
            authSessionVm.state.value.isInitializing && !timeoutReached
        }

        enableEdgeToEdge()

        logAuthCallback(intent.data)
        supabase.handleDeeplinks(
            intent = intent,
            onSessionSuccess = { session ->
                Log.i(TAG, "deeplink import success, userId=${session.user?.id ?: "unknown"}")
            },
            onError = { t ->
                Log.e(TAG, "deeplink import failed", t)
            }
        )

        setContent {
            SyncTheme {
                SyncNavApp(modifier = Modifier.fillMaxSize())
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // 当 activity 已在栈顶时也要处理一次。
        Log.i(TAG, "onNewIntent data=${intent.data}")
        logAuthCallback(intent.data)
        supabase.handleDeeplinks(
            intent = intent,
            onSessionSuccess = { session ->
                Log.i(TAG, "deeplink import success, userId=${session.user?.id ?: "unknown"}")
            },
            onError = { t ->
                Log.e(TAG, "deeplink import failed", t)
            }
        )
    }

    private companion object {
        private const val TAG = "AuthDeeplink"
    }

    private fun logAuthCallback(uri: Uri?) {
        if (uri == null) return
        val err = uri.getQueryParameter("error")
        val code = uri.getQueryParameter("error_code")
        val desc = uri.getQueryParameter("error_description")
        if (!err.isNullOrBlank() || !desc.isNullOrBlank()) {
            Log.e(TAG, "auth callback error(query): error=$err code=$code desc=$desc")
        }
        val fragment = uri.fragment.orEmpty()
        if (fragment.contains("error=") || fragment.contains("error_description=")) {
            Log.e(TAG, "auth callback fragment=$fragment")
        }
        val isRecovery = fragment.contains("type=recovery") || uri.getQueryParameter("type") == "recovery"
        if (isRecovery) {
            Log.i(TAG, "auth callback is recovery link. Showing update password dialog.")
            AuthEventManager.showPasswordResetDialog.value = true
        }

        if (uri.host == "authorize_sso") {
            val sessionId = uri.getQueryParameter("sessionId")
            if (!sessionId.isNullOrBlank()) {
                val returnPkg = uri.getQueryParameter("returnPkg")?.trim()?.takeIf { it.isNotBlank() }
                Log.i(TAG, "SSO authorize request detected: sessionId=$sessionId returnPkg=$returnPkg")
                AuthEventManager.pendingSsoAuthorize.value = PendingSsoAuthorize(
                    sessionId = sessionId,
                    returnPackage = returnPkg,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@androidx.compose.runtime.Composable
private fun AppPreview() {
    SyncTheme {
        SyncNavApp(modifier = Modifier.fillMaxSize())
    }
}