package cn.verlu.cloud

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import cn.verlu.cloud.data.local.AndroidPlatformContext
import cn.verlu.cloud.di.ensureKoinStarted
import cn.verlu.cloud.di.AppGraph
import cn.verlu.cloud.presentation.auth.AuthDeepLinkBus
import cn.verlu.cloud.presentation.files.IncomingShareBus
import cn.verlu.cloud.presentation.files.ShareIntentParser
import cn.verlu.cloud.presentation.update.CloudAndroidUpdateGate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : ComponentActivity() {

    private val authInitializing = AtomicBoolean(true)
    private var splashTimeoutReached = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        ensureKoinStarted()
        val graph = GlobalContext.get().get<AppGraph>()
        lifecycleScope.launch {
            graph.authUseCases.observeSession().collect { state ->
                authInitializing.set(state.isInitializing)
                runOnUiThread { window.decorView.invalidate() }
            }
        }
        lifecycleScope.launch {
            delay(3_000)
            splashTimeoutReached = true
            runOnUiThread { window.decorView.invalidate() }
        }
        splashScreen.setKeepOnScreenCondition {
            authInitializing.get() && !splashTimeoutReached
        }

        AndroidPlatformContext.init(applicationContext)
        intent?.dataString?.let { AuthDeepLinkBus.links.tryEmit(it) }
        handleShareIntent(intent)

        setContent {
            App()
            CloudAndroidUpdateGate()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.dataString?.let { AuthDeepLinkBus.links.tryEmit(it) }
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        val picks = ShareIntentParser.parse(applicationContext, intent)
        if (picks.isNotEmpty()) {
            IncomingShareBus.offer(picks)
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
