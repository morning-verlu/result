package cn.verlu.music.presentation.navigation

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.verlu.music.R
import cn.verlu.music.presentation.music.vm.MusicDrawerViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicDrawerSheetContent(
    viewModel: MusicDrawerViewModel = hiltViewModel(),
    onNavigateDownloadManager: () -> Unit = {}
) {
    val context = LocalContext.current
    val endAt by viewModel.endAtEpochMs.collectAsStateWithLifecycle()
    val scheduledMin by viewModel.scheduledSleepMinutes.collectAsStateWithLifecycle()
    val cacheBytes by viewModel.cacheBytes.collectAsStateWithLifecycle()
    val resolveCacheCount by viewModel.resolveCacheCount.collectAsStateWithLifecycle()

    var nowTick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showCustomTimer by remember { mutableStateOf(false) }
    var showClearExplain by remember { mutableStateOf(false) }
    val timePickerState = rememberTimePickerState(
        initialHour = 0,
        initialMinute = 45,
        is24Hour = true
    )

    LaunchedEffect(Unit) {
        viewModel.refreshCacheSize()
        while (true) {
            delay(1000)
            nowTick = System.currentTimeMillis()
        }
    }

    if (showClearExplain) {
        AlertDialog(
            onDismissRequest = { showClearExplain = false },
            title = { Text(stringResource(R.string.drawer_clear_confirm_title)) },
            text = { Text(stringResource(R.string.drawer_clear_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearExplain = false
                        viewModel.clearPlaybackCache { msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            viewModel.refreshCacheSize()
                        }
                    }
                ) {
                    Text(stringResource(R.string.drawer_clear_confirm_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearExplain = false }) {
                    Text(stringResource(R.string.drawer_clear_confirm_cancel))
                }
            }
        )
    }

    if (showCustomTimer) {
        TimePickerDialog(
            title = {
                Text(stringResource(R.string.drawer_sleep_custom_dialog_title))
            },
            onDismissRequest = { showCustomTimer = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val minutes =
                            timePickerState.hour * 60 + timePickerState.minute
                        if (minutes > 0) {
                            viewModel.setSleepTimerMinutes(minutes)
                        }
                        showCustomTimer = false
                    }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomTimer = false }) {
                    Text("取消")
                }
            }
        ) {
            TimePicker(state = timePickerState)
        }
    }

    ModalDrawerSheet(
        modifier = Modifier
            .fillMaxHeight()
            .widthIn(max = 300.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Rounded.LibraryMusic,
                    contentDescription = null,
                    modifier = Modifier.size(42.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    val titleStyle = MaterialTheme.typography.titleLarge
                    Text(
                        text = stringResource(R.string.app_name),
                        style = titleStyle.copy(
                            lineHeight = titleStyle.fontSize * 1.12f
                        ),
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.drawer_tagline_prefix),
                            style = MaterialTheme.typography.labelMedium.copy(
                                lineHeight = 16.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(
                            onClick = {
                                val url = context.getString(R.string.drawer_github_url)
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, url.toUri())
                                    )
                                }
                            },
                            modifier = Modifier.heightIn(min = 0.dp, max = 28.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.drawer_github_label),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    lineHeight = 16.sp
                                )
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = stringResource(R.string.drawer_sleep_title),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.drawer_sleep_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))

            val chipScroll = rememberScrollState()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(chipScroll),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = endAt == null,
                    onClick = { viewModel.cancelSleepTimer() },
                    label = { Text(stringResource(R.string.drawer_sleep_off)) }
                )
                listOf(15, 30, 60).forEach { min ->
                    FilterChip(
                        selected = scheduledMin == min,
                        onClick = { viewModel.setSleepTimerMinutes(min) },
                        label = { Text("${min} 分") }
                    )
                }
                FilterChip(
                    selected = false,
                    onClick = { showCustomTimer = true },
                    label = { Text(stringResource(R.string.drawer_sleep_custom)) }
                )
            }

            endAt?.let { end ->
                val rem = (end - nowTick).coerceAtLeast(0L)
                if (rem > 0L) {
                    Spacer(Modifier.height(8.dp))
                    val s = rem / 1000
                    val m = s / 60
                    val rs = s % 60
                    Text(
                        text = stringResource(
                            R.string.drawer_sleep_remain,
                            m,
                            rs.toString().padStart(2, '0')
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.drawer_cache_simple_title),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.drawer_cache_simple_size, formatBytes(cacheBytes)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick = { showClearExplain = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(stringResource(R.string.drawer_clear_cache_action))
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    viewModel.clearResolveCache { msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        viewModel.refreshCacheSize()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("清理歌词/解析缓存（$resolveCacheCount）")
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onNavigateDownloadManager,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("下载管理")
            }
        }
    }
}

private fun formatBytes(n: Long): String {
    if (n <= 0L) return "0 B"
    val kb = 1024L
    val mb = kb * kb
    val gb = mb * kb
    return when {
        n >= gb -> String.format("%.2f GB", n.toDouble() / gb)
        n >= mb -> String.format("%.2f MB", n.toDouble() / mb)
        n >= kb -> String.format("%.1f KB", n.toDouble() / kb)
        else -> "$n B"
    }
}
