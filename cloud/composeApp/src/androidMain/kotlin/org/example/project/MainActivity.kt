package cn.verlu.cloud

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import cn.verlu.cloud.data.local.AndroidPlatformContext
import cn.verlu.cloud.presentation.auth.AuthDeepLinkBus
import cn.verlu.cloud.presentation.files.IncomingShareBus
import cn.verlu.cloud.presentation.files.ShareIntentParser

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        AndroidPlatformContext.init(applicationContext)
        intent?.dataString?.let { AuthDeepLinkBus.links.tryEmit(it) }
        handleShareIntent(intent)

        setContent {
            App()
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