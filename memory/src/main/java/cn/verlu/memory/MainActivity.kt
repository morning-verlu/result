package cn.verlu.memory

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import cn.verlu.memory.presentation.auth.ui.MemoryAuthApp
import cn.verlu.memory.presentation.auth.vm.AuthEventManager
import cn.verlu.memory.ui.theme.SyncTheme
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
fun MemoryApp() = MemoryAuthApp()

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    SyncTheme {
        MemoryApp()
    }
}