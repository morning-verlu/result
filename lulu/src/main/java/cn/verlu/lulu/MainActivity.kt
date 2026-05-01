package cn.verlu.lulu

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import cn.verlu.lulu.presentation.auth.vm.AuthEventManager
import cn.verlu.lulu.presentation.auth.vm.PendingSsoAuthorize
import cn.verlu.lulu.presentation.navigation.LuluNavApp
import cn.verlu.lulu.ui.theme.SyncTheme
import dagger.hilt.android.AndroidEntryPoint
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.handleDeeplinks
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var supabase: SupabaseClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleAuthCallback(intent)
        setContent {
            SyncTheme {
                LuluNavApp()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthCallback(intent)
    }

    private fun handleAuthCallback(intent: Intent) {
        val uri = intent.data ?: return
        logAuthCallback(uri)
        when (uri.host) {
            "login" -> {
                supabase.handleDeeplinks(
                    intent = intent,
                    onError = { Log.e(TAG, "Auth deeplink failed", it) },
                )
            }
            "authorize_sso" -> {
                val sessionId = uri.getQueryParameter("sessionId")?.trim().orEmpty()
                if (sessionId.isNotBlank()) {
                    AuthEventManager.pendingSsoAuthorize.value =
                        PendingSsoAuthorize(
                            sessionId = sessionId,
                            returnPackage = uri.getQueryParameter("returnPkg")
                                ?.trim()
                                ?.takeIf { it.isNotBlank() },
                        )
                }
            }
        }
    }

    private fun logAuthCallback(uri: Uri?) {
        if (uri == null) return
        val fragment = uri.fragment.orEmpty()
        if (fragment.contains("error=") || fragment.contains("error_description=")) {
            Log.e(TAG, "Auth callback fragment=$fragment")
        }
        val isRecovery = fragment.contains("type=recovery") || uri.getQueryParameter("type") == "recovery"
        if (isRecovery) {
            AuthEventManager.showPasswordResetDialog.value = true
        }
    }

    private companion object {
        private const val TAG = "LuluAuth"
    }
}

@Preview(showBackground = true)
@androidx.compose.runtime.Composable
fun GreetingPreview() {
    SyncTheme {
        LuluNavApp(modifier = Modifier)
    }
}
