package cn.verlu.memory.presentation.lifestream.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import cn.verlu.memory.domain.model.LifeEntry
import cn.verlu.memory.domain.model.LifeEntryType
import cn.verlu.memory.domain.model.LifeMedia
import cn.verlu.memory.domain.model.SyncState
import cn.verlu.memory.presentation.auth.vm.AuthSessionViewModel
import cn.verlu.memory.presentation.lifestream.vm.LifeStreamUiState
import cn.verlu.memory.presentation.lifestream.vm.LifeStreamViewModel
import cn.verlu.memory.presentation.lifestream.vm.SearchTimeFilter
import cn.verlu.memory.presentation.lifestream.vm.formatDisplayTime
import cn.verlu.memory.presentation.navigation.LocalSnackbarHostState
import cn.verlu.memory.presentation.profile.ProfileScreen
import cn.verlu.memory.presentation.ui.MemoryLoadingIndicator
import cn.verlu.memory.presentation.ui.MemoryPullToRefreshIndicator
import coil3.compose.AsyncImage
import androidx.compose.foundation.shape.CircleShape
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

sealed interface LifeStreamRoute {
    data object Home : LifeStreamRoute
    data object Search : LifeStreamRoute
    data object Profile : LifeStreamRoute
    data object Record : LifeStreamRoute
    data class Detail(val entryId: String) : LifeStreamRoute
    data object Settings : LifeStreamRoute
}

private val RECORD_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
private val TIMELINE_DAY_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
private val TIMELINE_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private const val MAX_VIDEO_SIZE_BYTES = 50L * 1024L * 1024L

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LifeStreamScreen(
    route: LifeStreamRoute = LifeStreamRoute.Home,
    onNavigate: (LifeStreamRoute) -> Unit = {},
    onBack: () -> Unit = {},
    viewModel: LifeStreamViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val authVm: AuthSessionViewModel = hiltViewModel()
    val authState by authVm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val avatarUrl = authState.user?.userMetadata?.get("avatar_url")?.toString()?.trim('"')
        ?.takeIf { it.isNotBlank() && it != "null" }
    var exportPayload by remember { mutableStateOf<String?>(null) }
    var exportFileName by remember { mutableStateOf("memory-life-stream.json") }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    var showSyncDebugPanel by remember { mutableStateOf(false) }
    var playingVideoUri by remember { mutableStateOf<String?>(null) }
    var handledRecordCloseNonce by rememberSaveable { mutableIntStateOf(0) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val json = readTextFromUri(context, uri)
            if (json != null) viewModel.importFromJson(json)
            else scope.launch { snackbarHostState.showSnackbar("读取导入文件失败") }
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val payload = exportPayload
        if (uri != null && payload != null) {
            if (!writeTextToUri(context, uri, payload)) scope.launch { snackbarHostState.showSnackbar("导出写入失败") }
            else scope.launch { snackbarHostState.showSnackbar("导出成功") }
        }
        exportPayload = null
    }
    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        uris.forEach { viewModel.appendMedia(it.toString(), "image/*") }
    }
    val videoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val bytes = queryUriSizeBytes(context, it)
            if (bytes != null && bytes > MAX_VIDEO_SIZE_BYTES) {
                scope.launch { snackbarHostState.showSnackbar("视频超过 50MB，无法上传") }
            } else {
                viewModel.appendMedia(it.toString(), "video/*")
            }
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }
    LaunchedEffect(state.scrollToTopNonce) {
        if (state.scrollToTopNonce > 0 && state.timelineEntries.isNotEmpty()) listState.animateScrollToItem(0)
    }

    if (route is LifeStreamRoute.Record) {
        LaunchedEffect(state.recordCloseNonce) {
            if (state.recordCloseNonce > handledRecordCloseNonce) {
                handledRecordCloseNonce = state.recordCloseNonce
                onBack()
            }
        }
    }

    when (route) {
        LifeStreamRoute.Home -> {
            LaunchedEffect(route, state.timelineEntries.isEmpty()) {
                if (state.timelineEntries.isEmpty()) {
                    viewModel.refreshOnHomeVisibleIfEmpty()
                }
            }
            HomePage(
                state = state,
                avatarUrl = avatarUrl,
                drawerState = drawerState,
                listState = listState,
                onOpenSearch = {
                    onNavigate(LifeStreamRoute.Search)
                },
                onOpenProfile = {
                    onNavigate(LifeStreamRoute.Profile)
                },
                onOpenSettings = { onNavigate(LifeStreamRoute.Settings) },
                onOpenCreateRecord = {
                    viewModel.openCreateRecordPage()
                    onNavigate(LifeStreamRoute.Record)
                },
                onOpenDetail = { entry ->
                    onNavigate(LifeStreamRoute.Detail(entry.id))
                },
                onPlayVideo = { uri -> playingVideoUri = uri },
                onEdit = { entry ->
                    viewModel.openEditRecordPage(entry)
                    onNavigate(LifeStreamRoute.Record)
                },
                onDelete = { viewModel.deleteEntry(it) },
                onExportSingle = { entry ->
                    viewModel.exportSingleEntryAsJson(entry) { json ->
                        exportPayload = json
                        exportFileName = "memory-entry-${entry.id.take(8)}.json"
                        exportLauncher.launch(exportFileName)
                    }
                },
                onSyncNow = viewModel::syncNow,
                isRefreshing = state.isBusy,
                onRefresh = viewModel::refresh,
                onExportAll = {
                    viewModel.exportAsJson { json ->
                        exportPayload = json
                        exportFileName = "memory-life-stream.json"
                        exportLauncher.launch(exportFileName)
                    }
                },
                onImport = { importLauncher.launch(arrayOf("application/json")) },
                showSnackbar = { message -> scope.launch { snackbarHostState.showSnackbar(message) } },
            )
        }
        LifeStreamRoute.Search -> {
            SearchScreen(
                state = state,
                onBack = onBack,
                onKeywordChange = viewModel::updateSearchKeyword,
                onClear = viewModel::clearSearchKeyword,
                onSelectFilter = viewModel::setSearchTimeFilter,
                onEdit = {
                    viewModel.openEditRecordPage(it)
                    onNavigate(LifeStreamRoute.Record)
                },
                onDelete = { viewModel.deleteEntry(it) },
                onOpenDetail = { entry ->
                    onNavigate(LifeStreamRoute.Detail(entry.id))
                },
                onPlayVideo = { uri -> playingVideoUri = uri },
                onExportSingle = { entry ->
                    viewModel.exportSingleEntryAsJson(entry) { json ->
                        exportPayload = json
                        exportFileName = "memory-entry-${entry.id.take(8)}.json"
                        exportLauncher.launch(exportFileName)
                    }
                },
            )
        }
        LifeStreamRoute.Profile -> {
            ProfileScreen(
                onBack = onBack,
            )
        }
        LifeStreamRoute.Record -> {
            RecordScreen(
                state = state,
                onCancel = { viewModel.requestCloseRecordPage() },
                onSave = viewModel::saveRecord,
                onContentChanged = viewModel::updateDraftContent,
                onPickImage = { imageLauncher.launch("image/*") },
                onPickVideo = { videoLauncher.launch("video/*") },
                onOpenTimeDialog = viewModel::openTimeDialog,
                onRemoveMedia = viewModel::removeMediaAt,
                isSaving = state.isSavingRecord,
            )
            BackHandler { viewModel.requestCloseRecordPage() }
        }
        is LifeStreamRoute.Detail -> {
            val entry = state.allEntries.firstOrNull { it.id == route.entryId }
            if (entry != null) {
                DetailScreen(
                    entry = entry,
                    onBack = onBack,
                    onSyncSingle = { viewModel.syncSingleEntry(entry.id) },
                    onPlayVideo = { uri -> playingVideoUri = uri },
                    onEdit = {
                        viewModel.openEditRecordPage(entry)
                        onNavigate(LifeStreamRoute.Record)
                    },
                    onDelete = {
                        viewModel.deleteEntry(entry)
                        onBack()
                    },
                )
            } else {
                LaunchedEffect(route.entryId) {
                    onBack()
                }
            }
        }
        LifeStreamRoute.Settings -> {
            SettingsScreen(
                onBack = onBack,
                showCloudBadge = state.showCloudBadge,
                cloudSyncEnabled = state.cloudSyncEnabled,
                mediaCdnBaseUrl = state.mediaCdnBaseUrl,
                onShowCloudBadgeChange = viewModel::setShowCloudBadge,
                onCloudSyncEnabledChange = viewModel::setCloudSyncEnabled,
                onMediaCdnBaseUrlChange = viewModel::setMediaCdnBaseUrl,
                onOpenSyncDebugPanel = { showSyncDebugPanel = true },
            )
        }
    }

    if (state.isDiscardDialogVisible) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDiscardDialog,
            title = { Text("放弃这条记录？") },
            text = { Text("当前内容还没有保存。") },
            confirmButton = { TextButton(onClick = viewModel::discardAndCloseRecordPage) { Text("放弃") } },
            dismissButton = { TextButton(onClick = viewModel::dismissDiscardDialog) { Text("继续编辑") } },
        )
    }

    if (state.isTimeDialogVisible) {
        val initialDateTime = remember(state.customTimeInput) {
            runCatching {
                LocalDateTime.parse(state.customTimeInput.trim(), RECORD_TIME_FORMATTER)
            }.getOrElse { LocalDateTime.now() }
        }
        var selectedDateMillis by remember(state.customTimeInput) {
            mutableStateOf(
                initialDateTime
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli(),
            )
        }
        var showDatePicker by remember(state.customTimeInput) { mutableStateOf(true) }
        var showTimePicker by remember(state.customTimeInput) { mutableStateOf(false) }
        var selectedHour by remember(state.customTimeInput) { mutableIntStateOf(initialDateTime.hour) }
        var selectedMinute by remember(state.customTimeInput) { mutableIntStateOf(initialDateTime.minute) }

        if (showDatePicker) {
            val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDateMillis)
            DatePickerDialog(
                onDismissRequest = viewModel::dismissTimeDialog,
                confirmButton = {
                    TextButton(
                        onClick = {
                            selectedDateMillis = datePickerState.selectedDateMillis ?: selectedDateMillis
                            showDatePicker = false
                            showTimePicker = true
                        },
                    ) { Text("下一步") }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::dismissTimeDialog) { Text("取消") }
                },
            ) {
                DatePicker(
                    state = datePickerState,
                    showModeToggle = false,
                )
            }
        }

        if (showTimePicker) {
            val timePickerState = rememberTimePickerState(
                initialHour = selectedHour,
                initialMinute = selectedMinute,
                is24Hour = true,
            )
            AlertDialog(
                onDismissRequest = viewModel::dismissTimeDialog,
                title = { Text("选择时间") },
                text = {
                    TimePicker(state = timePickerState)
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            selectedHour = timePickerState.hour
                            selectedMinute = timePickerState.minute
                            val date = Instant.ofEpochMilli(selectedDateMillis)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate()
                            val target = LocalDateTime.of(
                                date.year,
                                date.month,
                                date.dayOfMonth,
                                selectedHour,
                                selectedMinute,
                            )
                            viewModel.updateCustomTimeInput(RECORD_TIME_FORMATTER.format(target))
                            viewModel.applyCustomTime()
                            showTimePicker = false
                        },
                    ) { Text("确定") }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::dismissTimeDialog) { Text("取消") }
                },
            )
        }
    }

    if (showSyncDebugPanel) {
        SyncStatusDebugDialog(
            entries = state.timelineEntries,
            onDismiss = { showSyncDebugPanel = false },
            onRetry = { entryId -> viewModel.syncSingleEntry(entryId) },
        )
    }
    playingVideoUri?.let { uri ->
        VideoPlayerDialog(
            videoUri = uri,
            onDismiss = { playingVideoUri = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomePage(
    state: LifeStreamUiState,
    avatarUrl: String?,
    drawerState: androidx.compose.material3.DrawerState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onOpenSearch: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenCreateRecord: () -> Unit,
    onOpenDetail: (LifeEntry) -> Unit,
    onPlayVideo: (String) -> Unit,
    onEdit: (LifeEntry) -> Unit,
    onDelete: (LifeEntry) -> Unit,
    onExportSingle: (LifeEntry) -> Unit,
    onSyncNow: () -> Unit,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onExportAll: () -> Unit,
    onImport: () -> Unit,
    showSnackbar: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val pullToRefreshState = rememberPullToRefreshState()
    val context = LocalContext.current
    val emptyIllustrationResId = remember {
        context.resources.getIdentifier("nulldata", "drawable", context.packageName)
    }
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(300.dp),
            ) {
                Text("Memory", modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("导出数据") },
                    onClick = {
                        scope.launch { drawerState.close() }
                        onExportAll()
                    },
                )
                DropdownMenuItem(
                    text = { Text("导入数据") },
                    onClick = {
                        scope.launch { drawerState.close() }
                        onImport()
                    },
                )
                DropdownMenuItem(
                    text = { Text("立即同步") },
                    onClick = {
                        scope.launch { drawerState.close() }
                        onSyncNow()
                    },
                )
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Text(
                    text = "预留内容",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
                DropdownMenuItem(
                    text = { Text("设置") },
                    onClick = {
                        scope.launch { drawerState.close() }
                        onOpenSettings()
                    },
                )
            }
        },
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = { },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "侧边栏")
                        }
                    },
                    actions = {
                        IconButton(onClick = onOpenSearch) {
                            Icon(Icons.Default.Search, contentDescription = "搜索")
                        }
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .clickable(onClick = onOpenProfile),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (avatarUrl != null) {
                                AsyncImage(
                                    model = avatarUrl,
                                    contentDescription = "个人信息",
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape),
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = "个人信息",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = onOpenCreateRecord) {
                    Icon(Icons.Default.Add, contentDescription = "记录")
                }
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 12.dp),
            ) {
                if (state.pendingSyncCount > 0) {
                    Text(
                        text = "待同步记录 ${state.pendingSyncCount} 条，网络恢复后自动同步",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                }
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = onRefresh,
                    state = pullToRefreshState,
                    modifier = Modifier.fillMaxSize(),
                    indicator = {
                        MemoryPullToRefreshIndicator(
                            state = pullToRefreshState,
                            isRefreshing = isRefreshing,
                            modifier = Modifier.align(Alignment.TopCenter),
                        )
                    },
                ) {
                    when {
                        state.timelineEntries.isEmpty() && state.isInitialLoading -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                MemoryLoadingIndicator(
                                    modifier = Modifier.size(34.dp),
                                    reason = "home_initial_loading_empty_local",
                                )
                            }
                        }
                        state.timelineEntries.isEmpty() -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                item(key = "empty-state") {
                                    if (emptyIllustrationResId != 0) {
                                        androidx.compose.foundation.Image(
                                            painter = painterResource(id = emptyIllustrationResId),
                                            contentDescription = null,
                                            modifier = Modifier.size(180.dp),
                                            contentScale = ContentScale.Fit,
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Image,
                                            contentDescription = null,
                                            modifier = Modifier.size(96.dp),
                                            tint = MaterialTheme.colorScheme.outlineVariant,
                                        )
                                    }
                                }
                            }
                        }
                        else -> {
                            val groupedEntries = remember(state.timelineEntries) {
                                state.timelineEntries.groupBy { entry ->
                                    formatTimelineDay(entry.createdAtEpochMs)
                                }
                            }
                            var collapsedDays by rememberSaveable { mutableStateOf(setOf<String>()) }
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                state = listState,
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                groupedEntries.forEach { (day, entriesInDay) ->
                                    item(key = "day-$day") {
                                        DaySectionHeader(
                                            day = day,
                                            isCollapsed = collapsedDays.contains(day),
                                            onToggle = {
                                                collapsedDays = if (collapsedDays.contains(day)) {
                                                    collapsedDays - day
                                                } else {
                                                    collapsedDays + day
                                                }
                                            },
                                        )
                                    }
                                    item(key = "group-$day") {
                                        androidx.compose.animation.AnimatedVisibility(
                                            visible = !collapsedDays.contains(day),
                                            enter = fadeIn() + expandVertically(),
                                            exit = fadeOut() + shrinkVertically(),
                                        ) {
                                            Column(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                entriesInDay.forEach { entry ->
                                                    EntryCard(
                                                        showCloudBadge = state.showCloudBadge,
                                                        entry = entry,
                                                        onOpenDetail = { onOpenDetail(entry) },
                                                        onPlayVideo = onPlayVideo,
                                                        onEdit = { onEdit(entry) },
                                                        onDelete = { onDelete(entry) },
                                                        onExportSingle = { onExportSingle(entry) },
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DaySectionHeader(
    day: String,
    isCollapsed: Boolean,
    onToggle: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(top = 2.dp, bottom = 2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = day,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (isCollapsed) {
                Text(
                    text = "展开",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EntryCard(
    showCloudBadge: Boolean,
    entry: LifeEntry,
    onOpenDetail: () -> Unit,
    onPlayVideo: (String) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onExportSingle: () -> Unit,
) {
    var menuExpanded by remember(entry.id) { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onOpenDetail, onLongClick = { menuExpanded = true }),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatTimelineTime(entry.createdAtEpochMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val isCloudSynced = showCloudBadge && entry.syncState == SyncState.SYNCED
                if (isCloudSynced) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CloudDone,
                            contentDescription = "已同步云端",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.size(4.dp))
                        Text(
                            text = "云端",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = entry.content,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (entry.mediaList.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(entry.mediaList) { media ->
                        MediaThumb(
                            media = media,
                            onVideoClick = onPlayVideo,
                        )
                    }
                }
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(text = { Text("编辑") }, onClick = { menuExpanded = false; onEdit() })
                DropdownMenuItem(text = { Text("删除") }, onClick = { menuExpanded = false; onDelete() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchScreen(
    state: LifeStreamUiState,
    onBack: () -> Unit,
    onKeywordChange: (String) -> Unit,
    onClear: () -> Unit,
    onSelectFilter: (SearchTimeFilter) -> Unit,
    onOpenDetail: (LifeEntry) -> Unit,
    onPlayVideo: (String) -> Unit,
    onEdit: (LifeEntry) -> Unit,
    onDelete: (LifeEntry) -> Unit,
    onExportSingle: (LifeEntry) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    var inputValue by remember { mutableStateOf(TextFieldValue(state.searchKeyword)) }
    LaunchedEffect(state.searchKeyword) {
        if (state.searchKeyword != inputValue.text) {
            inputValue = TextFieldValue(
                text = state.searchKeyword,
                selection = TextRange(state.searchKeyword.length),
            )
        }
    }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        inputValue = inputValue.copy(selection = TextRange(inputValue.text.length))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    TextField(
                        value = inputValue,
                        onValueChange = { value ->
                            inputValue = value
                            onKeywordChange(value.text)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .heightIn(min = 52.dp),
                        textStyle = MaterialTheme.typography.bodyMedium,
                        placeholder = {
                            Text(
                                text = "输入关键词...",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (inputValue.text.isNotBlank()) {
                                IconButton(
                                    onClick = {
                                        inputValue = TextFieldValue("")
                                        onClear()
                                    },
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "清空")
                                }
                            }
                        },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                            disabledIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {},
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.searchTimeFilter == SearchTimeFilter.ALL,
                    onClick = { onSelectFilter(SearchTimeFilter.ALL) },
                    label = { Text("全部") },
                )
                FilterChip(
                    selected = state.searchTimeFilter == SearchTimeFilter.DAYS_7,
                    onClick = { onSelectFilter(SearchTimeFilter.DAYS_7) },
                    label = { Text("最近7天") },
                )
                FilterChip(
                    selected = state.searchTimeFilter == SearchTimeFilter.DAYS_30,
                    onClick = { onSelectFilter(SearchTimeFilter.DAYS_30) },
                    label = { Text("最近30天") },
                )
            }
            Spacer(Modifier.height(8.dp))
            if (state.searchResults.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("没有找到相关记录")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.searchResults, key = { it.id }) { entry ->
                        EntryCard(
                            showCloudBadge = state.showCloudBadge,
                            entry = entry,
                            onOpenDetail = { onOpenDetail(entry) },
                            onPlayVideo = onPlayVideo,
                            onEdit = { onEdit(entry) },
                            onDelete = { onDelete(entry) },
                            onExportSingle = { onExportSingle(entry) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailScreen(
    entry: LifeEntry,
    onBack: () -> Unit,
    onSyncSingle: () -> Unit,
    onPlayVideo: (String) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var showDeleteDialog by remember(entry.id) { mutableStateOf(false) }
    val imageUrls = remember(entry.id, entry.mediaList) {
        entry.mediaList
            .filter { it.mimeType?.startsWith("image/") == true }
            .map { it.uri }
    }
    var previewImageIndex by remember(entry.id) { mutableStateOf<Int?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(
                        onClick = onSyncSingle,
                        enabled = entry.syncState == SyncState.LOCAL_ONLY || entry.syncState == SyncState.ERROR,
                    ) {
                        Icon(
                            imageVector = if (entry.syncState == SyncState.SYNCED) Icons.Default.CloudDone else Icons.Default.FileUpload,
                            contentDescription = if (entry.syncState == SyncState.SYNCED) "已同步云端" else "同步到云端",
                            tint = if (entry.syncState == SyncState.SYNCED) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "编辑")
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "删除")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = formatDisplayTime(entry.createdAtEpochMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = entry.content,
                style = MaterialTheme.typography.bodyLarge,
            )
            if (entry.mediaList.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(entry.mediaList) { media ->
                        MediaThumb(
                            media = media,
                            onImageClick = {
                                val idx = imageUrls.indexOf(media.uri)
                                if (idx >= 0) previewImageIndex = idx
                            },
                            onVideoClick = onPlayVideo,
                        )
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除这条记录？") },
            text = { Text("删除后不可恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                        onBack()
                    },
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("取消") }
            },
        )
    }
    previewImageIndex?.let { idx ->
        ImagePreviewSheet(
            imageUrls = imageUrls,
            initialIndex = idx,
            onDismiss = { previewImageIndex = null },
        )
    }
}

/**
 * 视频缩略图数据（封面 + 时长文字），放在进程级 LruCache 里，
 * 导航返回时 initialValue 直接命中内存，不再从 null 开始。
 */
private data class VideoThumbData(val bitmap: android.graphics.Bitmap, val duration: String?)
private val videoThumbCache = android.util.LruCache<String, VideoThumbData>(30)

@Composable
private fun rememberResolvedImageUri(uri: String): androidx.compose.runtime.State<String> {
    val context = LocalContext.current
    val cacheKey = remember(uri) { stableMediaCacheKey(uri) }
    val existing = remember(cacheKey, uri) { findCachedImageUriIfExists(context, cacheKey, uri) }
    return produceState(initialValue = existing ?: uri, key1 = cacheKey, key2 = uri) {
        if (existing != null) return@produceState
        value = withContext(Dispatchers.IO) {
            cacheImageLocally(context, cacheKey, uri)
        }
    }
}

@Composable
private fun MediaThumb(
    media: LifeMedia,
    onImageClick: (() -> Unit)? = null,
    onVideoClick: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val resolvedImageUri by rememberResolvedImageUri(media.uri)
    val thumbKey = remember(media.uri) { stableMediaCacheKey(media.uri) }
    Box {
        if (media.mimeType?.startsWith("image/") == true) {
            // 优先使用本地落盘后的 URI，避免签名 URL 刷新导致重复请求。
            AsyncImage(
                model = resolvedImageUri,
                contentDescription = null,
                modifier = Modifier
                    .size(84.dp)
                    .let { base ->
                        if (onImageClick != null) base.clickable(onClick = onImageClick) else base
                    },
            )
        } else {
            // initialValue 直接从内存缓存取，导航返回时立即显示已有数据
            val thumbState = produceState(
                initialValue = videoThumbCache.get(thumbKey),
                key1 = thumbKey,
            ) {
                if (value != null) return@produceState
                value = withContext(Dispatchers.IO) {
                    loadVideoThumbData(context, thumbKey, media.uri)
                }
            }
            val thumbData = thumbState.value
            Card(
                modifier = Modifier
                    .size(84.dp)
                    .let { base ->
                        if (onVideoClick != null) {
                            base.clickable(onClick = { onVideoClick(media.uri) })
                        } else {
                            base
                        }
                    },
                colors = CardDefaults.cardColors(),
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (thumbData != null) {
                        androidx.compose.foundation.Image(
                            bitmap = thumbData.bitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Default.Videocam, contentDescription = null)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.26f)),
                    )
                    Icon(
                        Icons.Default.PlayCircleFilled,
                        contentDescription = null,
                        tint = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier
                            .size(30.dp)
                            .align(Alignment.Center),
                    )
                    if (thumbData?.duration != null) {
                        Text(
                            text = thumbData.duration,
                            color = androidx.compose.ui.graphics.Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 2.dp, bottom = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordScreen(
    state: LifeStreamUiState,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    onContentChanged: (String) -> Unit,
    onPickImage: () -> Unit,
    onPickVideo: () -> Unit,
    onOpenTimeDialog: () -> Unit,
    onRemoveMedia: (Int) -> Unit,
    isSaving: Boolean,
) {
    val focusRequester = remember { FocusRequester() }
    var contentValue by remember { mutableStateOf(TextFieldValue(state.draftContent)) }
    LaunchedEffect(state.draftContent) {
        if (state.draftContent != contentValue.text) {
            contentValue = TextFieldValue(
                text = state.draftContent,
                selection = TextRange(state.draftContent.length),
            )
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        contentValue = contentValue.copy(selection = TextRange(contentValue.text.length))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("记录") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, contentDescription = "取消")
                    }
                },
                actions = {
                    TextButton(
                        onClick = onSave,
                        enabled = !isSaving,
                    ) {
                        if (isSaving) {
                            MemoryLoadingIndicator(
                                modifier = Modifier.size(18.dp),
                                reason = "record_save",
                            )
                        } else {
                            Text("保存")
                        }
                    }
                },
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceAround,
            ) {
                IconButton(onClick = onPickImage) { Icon(Icons.Default.Image, contentDescription = "图片") }
                IconButton(onClick = onPickVideo) { Icon(Icons.Default.Videocam, contentDescription = "视频") }
                IconButton(onClick = onOpenTimeDialog) { Icon(Icons.Default.AccessTime, contentDescription = "时间") }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp),
        ) {
            TextField(
                value = contentValue,
                onValueChange = { value ->
                    contentValue = value
                    onContentChanged(value.text)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .focusRequester(focusRequester),
                placeholder = { Text("今天在想什么...") },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    disabledContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    disabledIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
            )
            if (state.draftMediaList.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.draftMediaList.size) { index ->
                        val media = state.draftMediaList[index]
                        Box {
                            MediaThumb(media = media)
                            IconButton(
                                onClick = { onRemoveMedia(index) },
                                modifier = Modifier.align(Alignment.TopEnd),
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "删除")
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 加载视频缩略图（封面帧 + 时长）：
 * 优先读内存缓存 → 磁盘缓存（JPEG） → MediaMetadataRetriever 提取。
 * 提取后同时写入内存和磁盘缓存，一次 retriever 同时拿到封面和时长。
 */
private fun loadVideoThumbData(context: Context, thumbKey: String, uri: String): VideoThumbData? {
    // 1. 磁盘缓存命中
    val cacheFile = videoThumbDiskFile(context, thumbKey)
    val durFile = File(cacheFile.parent, cacheFile.nameWithoutExtension + ".dur")
    if (cacheFile.exists() && cacheFile.length() > 0) {
        val cached = runCatching { BitmapFactory.decodeFile(cacheFile.absolutePath) }.getOrNull()
        if (cached != null) {
            val dur = runCatching { durFile.readText().ifBlank { null } }.getOrNull()
            val data = VideoThumbData(cached, dur)
            videoThumbCache.put(thumbKey, data)
            return data
        }
    }
    // 2. 提取帧（frame + duration 一次完成）
    return runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            val parsed = Uri.parse(uri)
            if (parsed.scheme == "http" || parsed.scheme == "https") {
                retriever.setDataSource(uri, emptyMap())
            } else {
                retriever.setDataSource(context, parsed)
            }
            val rawBitmap = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: return@runCatching null
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
            val duration = durationMs?.let { formatVideoDuration(it) }
            // 缩放到 252px 节省内存和磁盘
            val thumb = android.graphics.Bitmap.createScaledBitmap(rawBitmap, 252, 252, true)
            if (thumb !== rawBitmap) rawBitmap.recycle()
            // 3. 写磁盘缓存
            runCatching {
                cacheFile.parentFile?.mkdirs()
                cacheFile.outputStream().use { thumb.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, it) }
                if (duration != null) durFile.writeText(duration) else durFile.delete()
            }
            val data = VideoThumbData(thumb, duration)
            videoThumbCache.put(thumbKey, data)
            data
        } finally {
            runCatching { retriever.release() }
        }
    }.getOrNull()
}

private fun videoThumbDiskFile(context: Context, thumbKey: String): File {
    val dir = File(context.cacheDir, "memory-video-thumb").apply { mkdirs() }
    return File(dir, "${sha256(thumbKey).take(24)}.jpg")
}

private fun stableMediaCacheKey(uri: String): String {
    val parsed = Uri.parse(uri)
    val scheme = parsed.scheme.orEmpty().lowercase()
    if (scheme != "http" && scheme != "https") return uri
    val host = parsed.host.orEmpty().lowercase()
    val path = parsed.encodedPath.orEmpty()
    return if (host.isNotBlank() || path.isNotBlank()) "$host$path" else uri
}

private fun findCachedImageUriIfExists(context: Context, cacheKey: String, uri: String): String? {
    val parsed = Uri.parse(uri)
    val scheme = parsed.scheme.orEmpty().lowercase()
    if (scheme != "http" && scheme != "https") return uri
    val cacheFile = imageDiskCacheFile(context, cacheKey, uri)
    return if (cacheFile.exists() && cacheFile.length() > 0L) cacheFile.toURI().toString() else null
}

private fun cacheImageLocally(context: Context, cacheKey: String, uri: String): String {
    val parsed = Uri.parse(uri)
    val scheme = parsed.scheme.orEmpty().lowercase()
    if (scheme != "http" && scheme != "https") return uri
    val cacheFile = imageDiskCacheFile(context, cacheKey, uri)
    if (cacheFile.exists() && cacheFile.length() > 0L) return cacheFile.toURI().toString()
    return runCatching {
        URL(uri).openStream().use { input ->
            cacheFile.parentFile?.mkdirs()
            cacheFile.outputStream().use { output -> input.copyTo(output) }
        }
        cacheFile.toURI().toString()
    }.getOrElse { uri }
}

private fun imageDiskCacheFile(context: Context, cacheKey: String, uri: String): File {
    val dir = File(context.cacheDir, "memory-image-cache").apply { mkdirs() }
    val ext = uri.substringAfterLast('.', "").substringBefore('?').ifBlank { "img" }
    return File(dir, "${sha256(cacheKey).take(24)}.$ext")
}

@Composable
private fun VideoPlayerDialog(
    videoUri: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val player = remember(videoUri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(videoUri)))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) {
        onDispose {
            runCatching {
                player.stop()
                player.release()
            }
        }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(androidx.compose.ui.graphics.Color.Black),
        ) {
            androidx.compose.ui.viewinterop.AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    PlayerView(context).apply {
                        useController = true
                        controllerAutoShow = true
                        this.player = player
                    }
                },
                update = { view -> view.player = player },
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd),
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "关闭",
                    tint = androidx.compose.ui.graphics.Color.White,
                )
            }
        }
    }
}



private fun formatVideoDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

private fun sha256(value: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    return digest.digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}

private fun queryUriSizeBytes(context: Context, uri: Uri): Long? {
    val cursor = context.contentResolver.query(
        uri,
        arrayOf(android.provider.OpenableColumns.SIZE),
        null,
        null,
        null,
    )
    cursor?.use {
        val sizeColumn = it.getColumnIndex(android.provider.OpenableColumns.SIZE)
        if (sizeColumn >= 0 && it.moveToFirst()) {
            return it.getLong(sizeColumn).takeIf { size -> size > 0L }
        }
    }
    return null
}

private fun readTextFromUri(context: Context, uri: Uri): String? =
    runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()

private fun writeTextToUri(context: Context, uri: Uri, value: String): Boolean =
    runCatching {
        context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(value) }
        true
    }.getOrDefault(false)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    onBack: () -> Unit,
    showCloudBadge: Boolean,
    cloudSyncEnabled: Boolean,
    mediaCdnBaseUrl: String,
    onShowCloudBadgeChange: (Boolean) -> Unit,
    onCloudSyncEnabledChange: (Boolean) -> Unit,
    onMediaCdnBaseUrlChange: (String) -> Unit,
    onOpenSyncDebugPanel: () -> Unit,
) {
    var versionTapCount by remember { mutableIntStateOf(0) }
    var lastVersionTapAt by remember { mutableLongStateOf(0L) }
    var showCloudEnableConfirmDialog by remember { mutableStateOf(false) }
    var showCdnEditDialog by remember { mutableStateOf(false) }
    var cdnInput by remember(mediaCdnBaseUrl) { mutableStateOf(mediaCdnBaseUrl) }
    val unlockTapTarget = 7
    val unlockWindowMs = 1_500L

    fun handleVersionTap() {
        val now = System.currentTimeMillis()
        versionTapCount = if (now - lastVersionTapAt <= unlockWindowMs) versionTapCount + 1 else 1
        lastVersionTapAt = now
        if (versionTapCount >= unlockTapTarget) {
            versionTapCount = 0
            onOpenSyncDebugPanel()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            ListItem(
                headlineContent = { Text("云同步") },
                supportingContent = { Text("控制是否开启云同步（默认关闭）") },
                trailingContent = {
                    Switch(
                        checked = cloudSyncEnabled,
                        onCheckedChange = { enabled ->
                            when {
                                enabled && !cloudSyncEnabled -> showCloudEnableConfirmDialog = true
                                !enabled && cloudSyncEnabled -> onCloudSyncEnabledChange(false)
                            }
                        },
                    )
                },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("云端状态标识") },
                supportingContent = { Text("控制首页卡片右上角“云端”图标提醒（默认开启）") },
                trailingContent = {
                    Switch(
                        checked = showCloudBadge,
                        onCheckedChange = onShowCloudBadgeChange,
                    )
                },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("媒体 CDN 地址") },
                supportingContent = {
                    Text(
                        if (mediaCdnBaseUrl.isBlank()) "未设置（将使用源站）" else mediaCdnBaseUrl,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                modifier = Modifier.clickable { showCdnEditDialog = true },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("应用信息") },
                supportingContent = {
                    val context = LocalContext.current
                    Text("版本 ${readAppVersionName(context)}")
                },
                modifier = Modifier.clickable(onClick = ::handleVersionTap),
            )
        }
    }
    if (showCloudEnableConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showCloudEnableConfirmDialog = false },
            title = { Text("开启云同步") },
            text = { Text("如果未注册 cloud 项目账号，该功能可能无法正常使用。是否继续开启？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCloudEnableConfirmDialog = false
                        onCloudSyncEnabledChange(true)
                    },
                ) { Text("继续开启") }
            },
            dismissButton = {
                TextButton(onClick = { showCloudEnableConfirmDialog = false }) { Text("取消") }
            },
        )
    }
    if (showCdnEditDialog) {
        AlertDialog(
            onDismissRequest = { showCdnEditDialog = false },
            title = { Text("设置媒体 CDN 地址") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("示例：https://img.jkot.net（留空表示使用源站）")
                    OutlinedTextField(
                        value = cdnInput,
                        onValueChange = { cdnInput = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onMediaCdnBaseUrlChange(cdnInput)
                        showCdnEditDialog = false
                    },
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showCdnEditDialog = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun SyncStatusDebugDialog(
    entries: List<LifeEntry>,
    onDismiss: () -> Unit,
    onRetry: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("同步状态面板") },
        text = {
            if (entries.isEmpty()) {
                Text("暂无记录")
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(entries, key = { it.id }) { entry ->
                        val uploaded = entry.mediaList.count { media ->
                            media.uri.startsWith("http://") || media.uri.startsWith("https://")
                        }
                        val total = entry.mediaList.size
                        val canRetry = entry.syncState == SyncState.LOCAL_ONLY || entry.syncState == SyncState.ERROR
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("Entry: ${entry.id.take(12)}")
                                Text("状态: ${entry.syncState}")
                                Text("重试: ${entry.retryCount}")
                                Text("媒体: 已上传 $uploaded / $total")
                                Text("云端: ${if (entry.syncState == SyncState.SYNCED) "已确认" else "未确认"}")
                                if (canRetry) {
                                    TextButton(onClick = { onRetry(entry.id) }) {
                                        Text("手动重试")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

private fun readAppVersionName(context: Context): String {
    val packageName = context.packageName
    return runCatching {
        val packageInfo = context.packageManager.getPackageInfo(packageName, 0)
        packageInfo.versionName ?: "unknown"
    }.getOrDefault("unknown")
}

private fun formatTimelineDay(epochMs: Long): String =
    TIMELINE_DAY_FORMATTER.format(
        Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalDate(),
    )

private fun formatTimelineTime(epochMs: Long): String =
    TIMELINE_TIME_FORMATTER.format(
        Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalDateTime(),
    )
