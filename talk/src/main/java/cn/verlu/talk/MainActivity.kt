package cn.verlu.talk

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import cn.verlu.talk.presentation.auth.vm.AuthEventManager
import cn.verlu.talk.presentation.auth.vm.AuthSessionViewModel
import cn.verlu.talk.presentation.navigation.TalkNavApp
import cn.verlu.talk.presentation.theme.TalkTheme
import dagger.hilt.android.AndroidEntryPoint
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.handleDeeplinks
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var supabase: SupabaseClient

    private val authSessionVm: AuthSessionViewModel by viewModels()

    private var splashTimeoutReached = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            delay(3_000)
            splashTimeoutReached = true
        }

        splashScreen.setKeepOnScreenCondition {
            authSessionVm.state.value.isInitializing && !splashTimeoutReached
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
            },
        )

        setContent {
            TalkTheme {
                TalkNavApp()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        Log.i(TAG, "onNewIntent data=${intent.data}")
        logAuthCallback(intent.data)
        supabase.handleDeeplinks(
            intent = intent,
            onSessionSuccess = { session ->
                Log.i(TAG, "deeplink import success, userId=${session.user?.id ?: "unknown"}")
            },
            onError = { t ->
                Log.e(TAG, "deeplink import failed", t)
            },
        )
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
            Log.i(TAG, "auth callback is recovery link — show update password dialog")
            AuthEventManager.showPasswordResetDialog.value = true
        }
    }

    private companion object {
        private const val TAG = "TalkAuthDeeplink"
    }
}
