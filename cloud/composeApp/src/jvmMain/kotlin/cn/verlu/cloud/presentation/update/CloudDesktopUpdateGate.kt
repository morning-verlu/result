package cn.verlu.cloud.presentation.update

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import cn.verlu.cloud.data.remote.CloudSupabase
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import java.awt.Desktop
import java.net.URI
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val DESKTOP_PACKAGE_NAME = "cn.verlu.cloud.desktop"
private const val CURRENT_DESKTOP_VERSION_CODE = 9

@Serializable
private data class AppReleaseRow(
    @SerialName("version_code") val versionCode: Int,
    @SerialName("version_name") val versionName: String,
    val title: String,
    val changelog: String,
    @SerialName("download_url") val downloadUrl: String,
    @SerialName("force_update") val forceUpdate: Boolean = false,
    @SerialName("min_supported_version_code") val minSupportedVersionCode: Int = 0,
    @SerialName("rollout_percent") val rolloutPercent: Int = 100,
)

private data class ReleaseInfo(
    val versionName: String,
    val title: String,
    val changelog: String,
    val downloadUrl: String,
    val mandatory: Boolean,
)

@Composable
fun CloudDesktopUpdateGate() {
    var release by remember { mutableStateOf<ReleaseInfo?>(null) }

    LaunchedEffect(Unit) {
        val latest = runCatching {
            CloudSupabase.client.from("app_releases").select {
                filter {
                    eq("package_name", DESKTOP_PACKAGE_NAME)
                    eq("enabled", true)
                }
                order("version_code", Order.DESCENDING)
                limit(1)
            }.decodeList<AppReleaseRow>().firstOrNull()
        }.getOrNull() ?: return@LaunchedEffect

        if (latest.versionCode <= CURRENT_DESKTOP_VERSION_CODE) return@LaunchedEffect

        val mandatory = latest.forceUpdate || CURRENT_DESKTOP_VERSION_CODE < latest.minSupportedVersionCode
        val rollout = latest.rolloutPercent.coerceIn(1, 100)
        val installFingerprint = listOf(
            System.getProperty("user.name").orEmpty(),
            System.getProperty("os.name").orEmpty(),
            System.getProperty("os.arch").orEmpty(),
        ).joinToString("#")
        val bucket = ((installFingerprint.hashCode() and Int.MAX_VALUE) % 100) + 1
        if (!mandatory && bucket > rollout) return@LaunchedEffect

        release = ReleaseInfo(
            versionName = latest.versionName,
            title = latest.title,
            changelog = latest.changelog,
            downloadUrl = latest.downloadUrl,
            mandatory = mandatory,
        )
    }

    val info = release ?: return
    AlertDialog(
        onDismissRequest = { if (!info.mandatory) release = null },
        title = {
            Text(if (info.mandatory) "${info.title}（必须更新）" else info.title)
        },
        text = {
            Text(
                buildString {
                    append("发现新版本：")
                    append(info.versionName)
                    append("\n\n")
                    append(if (info.changelog.isBlank()) "修复已知问题并优化体验。" else info.changelog)
                },
            )
        },
        confirmButton = {
            TextButton(
                onClick = { openInBrowser(info.downloadUrl) },
            ) { Text("前往下载") }
        },
        dismissButton = if (!info.mandatory) {
            {
                TextButton(onClick = { release = null }) { Text("稍后") }
            }
        } else {
            null
        },
    )
}

private fun openInBrowser(url: String) {
    runCatching {
        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().browse(URI(url))
        }
    }
}
