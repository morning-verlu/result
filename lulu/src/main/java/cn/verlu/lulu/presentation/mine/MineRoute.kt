package cn.verlu.lulu.presentation.mine

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.verlu.lulu.domain.memory.MemorySyncSummary
import io.github.jan.supabase.auth.user.UserInfo
import kotlinx.coroutines.flow.collectLatest
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun MineRoute(
    user: UserInfo?,
    isOfflineSession: Boolean,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MineViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openInputStream(uri)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()
        }.onSuccess { json ->
            viewModel.importMemories(json)
        }.onFailure { throwable ->
            Toast.makeText(context, throwable.message ?: "读取文件失败", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is MineEffect.ShareMemoryJson -> {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/json"
                        putExtra(Intent.EXTRA_SUBJECT, "Lulu 记忆导出")
                        putExtra(Intent.EXTRA_TEXT, effect.json)
                    }
                    context.startActivity(Intent.createChooser(intent, "导出记忆 JSON"))
                }
            }
        }
    }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        viewModel.clearMessage()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AccountStatusCard(
            email = user?.email ?: user?.id ?: "已登录账号",
            sessionStatus = if (isOfflineSession) "离线会话" else "在线会话",
            syncStatus = state.syncSummary.statusText(isOfflineSession),
            lastSyncText = state.syncSummary.lastSyncText(),
        )
        MemoryHubSection(
            summary = state.syncSummary,
            isExporting = state.isExporting,
            isImporting = state.isImporting,
            onRetrySync = viewModel::retrySync,
            onExportMemories = viewModel::exportMemories,
            onImportMemories = { importLauncher.launch(arrayOf("application/json", "text/*", "*/*")) },
        )
        MemorySettingsSection(
            cloudSyncEnabled = state.cloudSyncEnabled,
            showCloudBadge = state.showCloudBadge,
            mediaCdnBaseUrl = state.mediaCdnBaseUrl,
            onCloudSyncChanged = viewModel::setCloudSyncEnabled,
            onCloudBadgeChanged = viewModel::setShowCloudBadge,
        )
        OutlinedButton(
            onClick = onSignOut,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text("退出登录")
        }
    }
}

@Composable
private fun AccountStatusCard(
    email: String,
    sessionStatus: String,
    syncStatus: String,
    lastSyncText: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(vertical = 6.dp)) {
            StatusRow(label = "当前账号", value = email)
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = DividerDefaults.color.copy(alpha = 0.6f),
            )
            StatusRow(label = "离线会话状态", value = sessionStatus)
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = DividerDefaults.color.copy(alpha = 0.6f),
            )
            StatusRow(label = "同步状态", value = syncStatus)
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = DividerDefaults.color.copy(alpha = 0.6f),
            )
            StatusRow(label = "上次同步", value = lastSyncText)
        }
    }
}

@Composable
private fun MemoryHubSection(
    summary: MemorySyncSummary,
    isExporting: Boolean,
    isImporting: Boolean,
    onRetrySync: () -> Unit,
    onExportMemories: () -> Unit,
    onImportMemories: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "记忆",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = summary.safetyText(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                IconButton(
                    onClick = onRetrySync,
                    enabled = !summary.isSyncing,
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "同步")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ActionButton(
                    text = if (summary.isSyncing) "同步中" else "同步",
                    icon = Icons.Default.CloudDone,
                    enabled = !summary.isSyncing,
                    onClick = onRetrySync,
                    modifier = Modifier.weight(1f),
                )
                ActionButton(
                    text = if (isExporting) "导出中" else "导出",
                    icon = Icons.Default.FileUpload,
                    enabled = !isExporting,
                    onClick = onExportMemories,
                    modifier = Modifier.weight(1f),
                )
                ActionButton(
                    text = if (isImporting) "导入中" else "导入",
                    icon = Icons.Default.FileDownload,
                    enabled = !isImporting,
                    onClick = onImportMemories,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun MemorySettingsSection(
    cloudSyncEnabled: Boolean,
    showCloudBadge: Boolean,
    mediaCdnBaseUrl: String,
    onCloudSyncChanged: (Boolean) -> Unit,
    onCloudBadgeChanged: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(vertical = 6.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "应用设置",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "各应用独立管理，这里先放记忆。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = DividerDefaults.color.copy(alpha = 0.6f),
            )
            SettingSwitchRow(
                title = "记忆云同步",
                description = "开启后同步旧版 Memory 和 Lulu 记忆。",
                checked = cloudSyncEnabled,
                onCheckedChange = onCloudSyncChanged,
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = DividerDefaults.color.copy(alpha = 0.6f),
            )
            SettingSwitchRow(
                title = "显示云端标记",
                description = "卡片右上角显示旧版样式的云端状态。",
                checked = showCloudBadge,
                onCheckedChange = onCloudBadgeChanged,
            )
            if (mediaCdnBaseUrl.isNotBlank()) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = DividerDefaults.color.copy(alpha = 0.6f),
                )
                StatusRow(label = "媒体地址", value = mediaCdnBaseUrl)
            }
        }
    }
}

@Composable
private fun ActionButton(
    text: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.size(6.dp))
        Text(text, maxLines = 1)
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun StatusRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = value,
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun MemorySyncSummary.statusText(isOfflineSession: Boolean): String = when {
    isOfflineSession -> "离线可用"
    isSyncing -> "同步中"
    failedCount > 0 -> "$failedCount 条失败"
    pendingCount > 0 -> "$pendingCount 条待同步"
    localOnlyCount > 0 -> "$localOnlyCount 条仅本地"
    syncedCount > 0 -> "已同步"
    else -> "暂无记忆"
}

private fun MemorySyncSummary.lastSyncText(): String =
    lastSyncedAt
        ?.atZone(ZoneId.systemDefault())
        ?.format(DateTimeFormatter.ofPattern("M月d日 HH:mm"))
        ?: "尚未同步"

private fun MemorySyncSummary.safetyText(): String = when {
    failedCount > 0 -> "有记忆同步失败，本地副本仍保留，可重试。"
    pendingCount > 0 -> "本地写入已完成，后台会继续补同步。"
    localOnlyCount > 0 -> "有仅本地记忆，登录在线后会进入同步队列。"
    syncedCount > 0 -> "Memory 的记忆已有云端副本，也可以随时导出本地 JSON。"
    else -> "这里管理 Memory 的同步和本地 JSON 导出。"
}
