package cn.verlu.cloud.presentation.update

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import cn.verlu.cloud.data.remote.createCloudSupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
fun CloudAndroidUpdateGate() {
    val context = LocalContext.current
    var release by remember { mutableStateOf<ReleaseInfo?>(null) }

    LaunchedEffect(Unit) {
        val packageName = context.packageName
        val currentVersionCode = context.packageManager.getPackageInfo(packageName, 0).longVersionCode.toInt()
        val client = createCloudSupabaseClient()
        val latest = runCatching {
            client.from("app_releases").select {
                filter {
                    eq("package_name", packageName)
                    eq("enabled", true)
                }
                order("version_code", Order.DESCENDING)
                limit(1)
            }.decodeList<AppReleaseRow>().firstOrNull()
        }.getOrNull() ?: return@LaunchedEffect

        if (latest.versionCode <= currentVersionCode) return@LaunchedEffect

        val mandatory = latest.forceUpdate || currentVersionCode < latest.minSupportedVersionCode
        val rollout = latest.rolloutPercent.coerceIn(1, 100)
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID).orEmpty()
        val bucket = ((androidId.plus(packageName).hashCode() and Int.MAX_VALUE) % 100) + 1
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
            TextButton(onClick = { startDownload(context.applicationContext, info) }) {
                Text("立即更新")
            }
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

private fun startDownload(context: Context, release: ReleaseInfo) {
    runCatching {
        val request = DownloadManager.Request(Uri.parse(release.downloadUrl))
            .setTitle("${context.packageName} ${release.versionName}")
            .setDescription("新版本安装包下载中")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setMimeType("application/vnd.android.package-archive")
            .setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                "${context.packageName}-${release.versionName}.apk",
            )
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.enqueue(request)
    }
}
