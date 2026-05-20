package cn.verlu.lulu.presentation.update

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import java.util.UUID

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
                if (!mandatory && !isInRollout(
                        installId = rolloutInstallId(context),
                        packageName = packageName,
                        rolloutPercent = latest.rolloutPercent,
                    )
                ) {
                    return@runCatching null
                }

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

    fun openDownloadPage(context: Context) {
        val release = _state.value.release ?: return
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(release.downloadUrl))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }.onSuccess {
            _state.update {
                it.copy(
                    message = "已打开下载页",
                    release = if (release.mandatory) release else null,
                )
            }
        }.onFailure {
            _state.update { state -> state.copy(message = "无法打开下载页，请稍后重试") }
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

private fun rolloutInstallId(context: Context): String {
    val prefs = context.getSharedPreferences("app_update_rollout", Context.MODE_PRIVATE)
    val existing = prefs.getString("install_id", null)
    if (!existing.isNullOrBlank()) return existing
    val generated = UUID.randomUUID().toString()
    prefs.edit().putString("install_id", generated).apply()
    return generated
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
                onClick = { viewModel.openDownloadPage(context.applicationContext) },
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
