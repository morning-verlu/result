package cn.verlu.cnchess.presentation.update

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun AppUpdateGate(
    showMessage: suspend (String) -> Unit,
    viewModel: AppUpdateViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.checkForUpdates(context.applicationContext)
    }

    LaunchedEffect(state.message) {
        val msg = state.message ?: return@LaunchedEffect
        showMessage(msg)
        viewModel.consumeMessage()
    }

    val release = state.release ?: return
    AlertDialog(
        onDismissRequest = {
            if (!release.mandatory) viewModel.postpone()
        },
        title = {
            Text(
                if (release.mandatory) "${release.title}（必须更新）"
                else release.title,
            )
        },
        text = {
            Text(
                buildString {
                    append("发现新版本：")
                    append(release.versionName)
                    append("\n\n")
                    if (release.changelog.isBlank()) {
                        append("修复已知问题并优化体验。")
                    } else {
                        append(release.changelog)
                    }
                },
            )
        },
        confirmButton = {
            TextButton(
                onClick = { viewModel.startDownload(context.applicationContext) },
            ) { Text("立即更新") }
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
