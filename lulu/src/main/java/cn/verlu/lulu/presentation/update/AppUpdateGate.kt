package cn.verlu.lulu.presentation.update

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
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
private data class AppReleaseRow(
    @SerialName("package_name") val packageName: String,
    @SerialName("version_code") val versionCode: Int,
    @SerialName("version_name") val versionName: String,
    val title: String,
    val changelog: String,
    @SerialName("download_url") val downloadUrl: String,
    @SerialName("force_update") val forceUpdate: Boolean = false,
    @SerialName("min_supported_version_code") val minSupportedVersionCode: Int = 0,
    @SerialName("rollout_percent") val rolloutPercent: Int = 100,
)

data class AppReleaseInfo(
    val versionCode: Int,
    val versionName: String,
    val title: String,
    val changelog: String,
    val downloadUrl: String,
    val mandatory: Boolean,
)

data class AppUpdateUiState(
    val isChecking: Boolean = false,
    val release: AppReleaseInfo? = null,
    val message: String? = null,
)

@HiltViewModel
class LuluAppUpdateViewModel @Inject constructor(
    private val supabase: SupabaseClient,
) : ViewModel() {
    private val _state = MutableStateFlow(AppUpdateUiState())
    val state: StateFlow<AppUpdateUiState> = _state.asStateFlow()

    fun checkForUpdates(context: Context) {
        if (_state.value.isChecking || _state.value.release != null) return
        viewModelScope.launch {
            _state.update { it.copy(isChecking = true) }
            runCatching {
                val packageName = context.packageName
                val currentVersionCode = context.packageManager
                    .getPackageInfo(packageName, 0)
                    .longVersionCode
                    .toInt()
                val latest = supabase.from("app_releases").select {
                    filter {
                        eq("package_name", packageName)
                        eq("enabled", true)
                    }
                    order("version_code", Order.DESCENDING)
                    limit(1)
                }.decodeList<AppReleaseRow>().firstOrNull() ?: return@runCatching null

                if (latest.versionCode <= currentVersionCode) return@runCatching null

                val mandatory = latest.forceUpdate || currentVersionCode < latest.minSupportedVersionCode
                val rollout = latest.rolloutPercent.coerceIn(1, 100)
                val androidId = Settings.Secure
                    .getString(context.contentResolver, Settings.Secure.ANDROID_ID)
                    .orEmpty()
                val bucket = ((androidId.plus(packageName).hashCode() and Int.MAX_VALUE) % 100) + 1
                if (!mandatory && bucket > rollout) return@runCatching null

                AppReleaseInfo(
                    versionCode = latest.versionCode,
                    versionName = latest.versionName,
                    title = latest.title,
                    changelog = latest.changelog,
                    downloadUrl = latest.downloadUrl,
                    mandatory = mandatory,
                )
            }.onSuccess { release ->
                _state.update { it.copy(isChecking = false, release = release) }
            }.onFailure {
                _state.update { it.copy(isChecking = false, message = "检查更新失败，请稍后再试") }
            }
        }
    }

    fun startDownload(context: Context) {
        val release = _state.value.release ?: return
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
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.enqueue(request)
        }.onSuccess {
            _state.update {
                it.copy(
                    message = "已开始下载，请在通知栏点击安装",
                    release = if (release.mandatory) release else null,
                )
            }
        }.onFailure {
            _state.update { state -> state.copy(message = "下载启动失败，请稍后重试") }
        }
    }

    fun postpone() {
        if (_state.value.release?.mandatory == true) return
        _state.update { it.copy(release = null) }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }
}

@Composable
fun LuluAppUpdateGate(
    showMessage: suspend (String) -> Unit,
    viewModel: LuluAppUpdateViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.checkForUpdates(context.applicationContext)
    }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        showMessage(message)
        viewModel.consumeMessage()
    }

    val release = state.release ?: return
    AlertDialog(
        onDismissRequest = {
            if (!release.mandatory) viewModel.postpone()
        },
        title = {
            Text(
                if (release.mandatory) "${release.title}（必须更新）" else release.title,
            )
        },
        text = {
            Text(
                buildString {
                    append("发现新版本：")
                    append(release.versionName)
                    append("\n\n")
                    append(release.changelog.ifBlank { "修复已知问题并优化体验。" })
                },
            )
        },
        confirmButton = {
            TextButton(
                onClick = { viewModel.startDownload(context.applicationContext) },
            ) {
                Text("立即更新")
            }
        },
        dismissButton = if (!release.mandatory) {
            {
                TextButton(onClick = viewModel::postpone) {
                    Text("稍后")
                }
            }
        } else {
            null
        },
    )
}
