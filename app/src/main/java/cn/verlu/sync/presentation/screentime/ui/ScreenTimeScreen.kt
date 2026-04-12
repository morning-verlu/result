package cn.verlu.sync.presentation.screentime.ui

import android.content.pm.PackageManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.verlu.sync.domain.model.AppUsageBreakdown
import cn.verlu.sync.domain.model.ScreenTimePeriod
import cn.verlu.sync.domain.model.SyncedScreenTimeReport
import cn.verlu.sync.presentation.auth.vm.AuthSessionViewModel
import cn.verlu.sync.presentation.screentime.UsageAccessSettingsOpener
import cn.verlu.sync.presentation.screentime.mvi.ScreenTimeContract
import cn.verlu.sync.presentation.screentime.vm.ScreenTimeViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ScreenTimeRoute(
    modifier: Modifier = Modifier,
    viewModel: ScreenTimeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        viewModel.dispatch(ScreenTimeContract.Intent.Load)
    }

    val authSessionViewModel: AuthSessionViewModel = hiltViewModel()
    val authState by authSessionViewModel.state.collectAsStateWithLifecycle()
    val myUserId = authState.user?.id

    LaunchedEffect(myUserId) {
        viewModel.dispatch(ScreenTimeContract.Intent.SetMyUserId(myUserId))
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.dispatch(ScreenTimeContract.Intent.Load)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        ScreenTimeScreen(
            state = state,
            modifier = Modifier.fillMaxSize(),
            onSelectPeriod = { viewModel.dispatch(ScreenTimeContract.Intent.SelectPeriod(it)) },
            onRefresh = { viewModel.dispatch(ScreenTimeContract.Intent.Refresh(isSilent = false)) },
            onToggleExpand = { viewModel.dispatch(ScreenTimeContract.Intent.ToggleExpand(it)) }
        )
    }
}

@Composable
private fun ScreenTimeScreen(
    state: ScreenTimeContract.State,
    modifier: Modifier = Modifier,
    onSelectPeriod: (ScreenTimePeriod) -> Unit,
    onRefresh: () -> Unit,
    onToggleExpand: (String) -> Unit
) {
    val context = LocalContext.current
    val springLow = spring<Float>(stiffness = Spring.StiffnessMediumLow)
    val springBouncy = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = state.period == ScreenTimePeriod.Today,
                    onClick = { onSelectPeriod(ScreenTimePeriod.Today) },
                    label = { Text("今天") }
                )
                FilterChip(
                    selected = state.period == ScreenTimePeriod.Last7Days,
                    onClick = { onSelectPeriod(ScreenTimePeriod.Last7Days) },
                    label = { Text("近 7 天") }
                )
            }

            if (!state.hasUsageAccess) {
                UsageAccessPromptCard(
                    onOpenSettings = { UsageAccessSettingsOpener.open(context) }
                )
            }

            state.listError?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error)
            }

            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (state.syncedReports.isEmpty() && !state.isRefreshing) {
                        item {
                            Text(
                                text = "暂无上报数据。他人在本页对应周期内打开过应用并授权后会出现。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    items(
                        items = state.syncedReports,
                        key = { it.stableKey }
                    ) { report ->
                        val highlight = report.userId == state.myUserId
                        SyncedScreenTimeCard(
                            report = report,
                            expanded = state.expandedRowKey == report.stableKey,
                            highlight = highlight,
                            springLow = springLow,
                            springBouncy = springBouncy,
                            onToggleExpand = { onToggleExpand(report.stableKey) }
                        )
                    }
                }
            }
    }
}

/**
 * 与天气页「定位授权」卡片同一套 Outlined 风格：点击跳转系统「使用情况访问」设置。
 */
@Composable
private fun UsageAccessPromptCard(onOpenSettings: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val accent = scheme.error
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenSettings() },
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.4f)),
        colors = CardDefaults.cardColors(
            containerColor = scheme.errorContainer.copy(alpha = 0.45f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Timer,
                contentDescription = null,
                modifier = Modifier.size(26.dp),
                tint = accent
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "开启使用情况访问",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "授权后统计本机各应用时长并同步到列表；未授权仍可查看他人上报。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.NavigateNext,
                contentDescription = null,
                tint = accent.copy(alpha = 0.75f)
            )
        }
    }
}

@Composable
private fun SyncedScreenTimeCard(
    report: SyncedScreenTimeReport,
    expanded: Boolean,
    highlight: Boolean,
    springLow: FiniteAnimationSpec<Float>,
    springBouncy: FiniteAnimationSpec<Float>,
    onToggleExpand: () -> Unit
) {
    val context = LocalContext.current
    val pm = context.packageManager
    val topThree = report.topApps.take(3)
    val hasTop = topThree.isNotEmpty()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (hasTop) Modifier.clickable(onClick = onToggleExpand) else Modifier),
        shape = RoundedCornerShape(12.dp),
        color = if (highlight) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    DeviceTitles(report)
                    Text(
                        text = formatDateTime(report.updatedAtMillis),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = formatDuration(report.totalForegroundMillis),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                if (hasTop) {
                    Icon(
                        imageVector = if (expanded) {
                            Icons.Filled.KeyboardArrowUp
                        } else {
                            Icons.Filled.KeyboardArrowDown
                        },
                        contentDescription = if (expanded) "收起" else "展开"
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded && hasTop,
                enter = fadeIn(springLow) + expandVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    expandFrom = Alignment.Top
                ),
                exit = fadeOut(springBouncy) + shrinkVertically(
                    animationSpec = spring(stiffness = Spring.StiffnessHigh),
                    shrinkTowards = Alignment.Top
                )
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HorizontalDivider(
                        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                    )
                    topThree.forEachIndexed { index, app ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${index + 1}. ${resolveTopAppDisplayName(pm, app)}",
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = formatDuration(app.foregroundMillis),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceTitles(report: SyncedScreenTimeReport) {
    val friendly = report.deviceFriendlyName.trim()
    val model = report.deviceModel.trim()
    val same = friendly.isNotEmpty() &&
        model.isNotEmpty() &&
        friendly.equals(model, ignoreCase = true)
    when {
        friendly.isNotEmpty() && model.isNotEmpty() && !same -> {
            Text(
                text = friendly,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = model,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        friendly.isNotEmpty() -> {
            Text(
                text = friendly,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        model.isNotEmpty() -> {
            Text(
                text = model,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        else -> {
            val id = report.userId
            Text(
                text = if (id.length > 14) "${id.take(12)}…" else id,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * 优先用本机 [PackageManager] 把包名解析成应用名；未安装或解析失败时用远端存的 [AppUsageBreakdown.appLabel]。
 */
private fun resolveTopAppDisplayName(pm: PackageManager, app: AppUsageBreakdown): String {
    val pkg = app.packageName.trim()
    if (pkg.isEmpty()) return app.appLabel.ifBlank { "—" }
    return runCatching {
        val info = pm.getApplicationInfo(pkg, 0)
        pm.getApplicationLabel(info).toString()
    }.getOrElse {
        app.appLabel.ifBlank { pkg }
    }
}

private fun formatDateTime(ts: Long): String {
    if (ts <= 0L) return "—"
    val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    return format.format(Date(ts))
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0L) return "0 分钟"
    val totalMinutes = ms / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 -> "${hours} 小时 ${minutes} 分钟"
        else -> "${minutes} 分钟"
    }
}
