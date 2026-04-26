package cn.verlu.memory

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import cn.verlu.memory.presentation.auth.vm.AuthEventManager
import cn.verlu.memory.presentation.auth.vm.AuthSessionViewModel
import cn.verlu.memory.presentation.navigation.MemoryNavApp
import cn.verlu.memory.ui.theme.SyncTheme
import dagger.hilt.android.AndroidEntryPoint
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.handleDeeplinks
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var supabase: SupabaseClient

    // 用 viewModels() 提前拿到 ViewModel，以便在 setContent 之前订阅状态
    private val authSessionViewModel: AuthSessionViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // installSplashScreen 必须在 super.onCreate 之后、setContent 之前调用
        val splashScreen = installSplashScreen()

        super.onCreate(savedInstanceState)

        // 保持 Splash 显示，直到认证状态初始化完成
        splashScreen.setKeepOnScreenCondition {
            authSessionViewModel.state.value.isInitializing
        }

        enableEdgeToEdge()
        logAuthCallback(intent.data)
        supabase.handleDeeplinks(intent)
        setContent {
            SyncTheme {
                MemoryApp()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        Log.i(TAG, "onNewIntent data=${intent.data}")
        logAuthCallback(intent.data)
        supabase.handleDeeplinks(intent)
    }

    private fun logAuthCallback(uri: Uri?) {
        if (uri == null) return
        val isRecovery = uri.fragment.orEmpty().contains("type=recovery") || uri.getQueryParameter("type") == "recovery"
        if (isRecovery) {
            AuthEventManager.showPasswordResetDialog.value = true
        }
    }

    private companion object {
        private const val TAG = "MemoryAuthDeeplink"
    }
}

@Composable
fun MemoryApp() = MemoryNavApp()

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    SyncTheme {
        MemoryApp()
    }
}