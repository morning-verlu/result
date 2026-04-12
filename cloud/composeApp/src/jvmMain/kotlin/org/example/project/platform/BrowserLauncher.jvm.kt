package cn.verlu.cloud.platform

import java.awt.Desktop
import java.net.URI

actual fun openExternalUrl(url: String): Result<Unit> = runCatching {
    check(Desktop.isDesktopSupported()) { "Desktop 浏览器能力不可用" }
    Desktop.getDesktop().browse(URI(url))
}
