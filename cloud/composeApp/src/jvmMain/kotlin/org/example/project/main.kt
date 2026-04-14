package cn.verlu.cloud

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import cn.verlu.cloud.platform.ensureDesktopProtocolRegistered
import cn.verlu.cloud.presentation.auth.AuthDeepLinkBus
import cn.verlu.cloud.presentation.update.CloudDesktopUpdateGate

fun main(args: Array<String>) {
    application {
        ensureDesktopProtocolRegistered()
        args.firstOrNull { it.startsWith("verlucloud://") }
            ?.let { AuthDeepLinkBus.links.tryEmit(it) }
        Window(
            onCloseRequest = ::exitApplication,
            title = "Cloud",
        ) {
            App()
            CloudDesktopUpdateGate()
        }
    }
}