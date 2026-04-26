package cn.verlu.memory.presentation.lifestream.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileDownload
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
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import cn.verlu.memory.domain.model.LifeEntry
import cn.verlu.memory.domain.model.LifeMedia
import cn.verlu.memory.presentation.auth.vm.AuthSessionViewModel
import cn.verlu.memory.presentation.lifestream.vm.LifeStreamUiState
import cn.verlu.memory.presentation.lifestream.vm.LifeStreamViewModel
import cn.verlu.memory.presentation.lifestream.vm.SearchTimeFilter
import cn.verlu.memory.presentation.lifestream.vm.formatDisplayTime
import cn.verlu.memory.presentation.navigation.LocalSnackbarHostState
import cn.verlu.memory.presentation.profile.ProfileScreen
import cn.verlu.memory.presentation.ui.MemoryLoadingIndicator
import coil3.compose.AsyncImage
import androidx.compose.foundation.shape.CircleShape
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Serializable
private sealed interface MemoryRoute : NavKey {
    @Serializable
    data object Home : MemoryRoute

    @Serializable
    data object Search : MemoryRoute

    @Serializable
    data object Profile : MemoryRoute

    @Serializable
    data object Record : MemoryRoute

    @Serializable
    data class Detail(val entryId: String) : MemoryRoute

    @Serializable
    data object Settings : MemoryRoute
}

private val RECORD_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LifeStreamScreen(
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
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
        uri?.let { viewModel.appendMedia(it.toString(), "video/*") }
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

    val backStack = rememberNavBackStack(MemoryRoute.Home)
    val pop: () -> Unit = { backStack.removeLastOrNull() }

    LaunchedEffect(state.isRecordPageOpen, backStack.lastOrNull()) {
        if (!state.isRecordPageOpen && backStack.lastOrNull() == MemoryRoute.Record) {
            pop()
        }
    }

    NavDisplay(
        backStack = backStack,
        onBack = pop,
        modifier = modifier.fillMaxSize(),
        transitionSpec = {
            slideInHorizontally(initialOffsetX = { it }) togetherWith
                slideOutHorizontally(targetOffsetX = { -it })
        },
        popTransitionSpec = {
            slideInHorizontally(initialOffsetX = { -it }) togetherWith
                slideOutHorizontally(targetOffsetX = { it })
        },
        predictivePopTransitionSpec = {
            slideInHorizontally(initialOffsetX = { -it }) togetherWith
                slideOutHorizontally(targetOffsetX = { it })
        },
        entryProvider = entryProvider {
            entry<MemoryRoute.Home> {
                HomePage(
                    state = state,
                    avatarUrl = avatarUrl,
                    drawerState = drawerState,
                    listState = listState,
                    onOpenSearch = {
                        viewModel.openSearchPage()
                        backStack.add(MemoryRoute.Search)
                    },
                    onOpenProfile = {
                        viewModel.openProfilePage()
                        backStack.add(MemoryRoute.Profile)
                    },
                    onOpenSettings = {
                        backStack.add(MemoryRoute.Settings)
                    },
                    onOpenCreateRecord = {
                        viewModel.openCreateRecordPage()
                        backStack.add(MemoryRoute.Record)
                    },
                    onOpenDetail = { entry ->
                        viewModel.openDetailPage(entry)
                        backStack.add(MemoryRoute.Detail(entry.id))
                    },
                    onEdit = { entry ->
                        viewModel.openEditRecordPage(entry)
                        backStack.add(MemoryRoute.Record)
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

            entry<MemoryRoute.Search> {
                SearchScreen(
                    state = state,
                    onBack = {
                        viewModel.closeSearchPage()
                        pop()
                    },
                    onKeywordChange = viewModel::updateSearchKeyword,
                    onClear = viewModel::clearSearchKeyword,
                    onSelectFilter = viewModel::setSearchTimeFilter,
                    onEdit = {
                        viewModel.openEditRecordPage(it)
                        backStack.add(MemoryRoute.Record)
                    },
                    onDelete = { viewModel.deleteEntry(it) },
                    onOpenDetail = { entry ->
                        viewModel.openDetailPage(entry)
                        backStack.add(MemoryRoute.Detail(entry.id))
                    },
                    onExportSingle = { entry ->
                        viewModel.exportSingleEntryAsJson(entry) { json ->
                            exportPayload = json
                            exportFileName = "memory-entry-${entry.id.take(8)}.json"
                            exportLauncher.launch(exportFileName)
                        }
                    },
                )
            }

            entry<MemoryRoute.Profile> {
                ProfileScreen(
                    onBack = {
                        viewModel.closeProfilePage()
                        pop()
                    },
                )
            }

            entry<MemoryRoute.Record> {
                RecordScreen(
                    state = state,
                    onCancel = viewModel::requestCloseRecordPage,
                    onSave = viewModel::saveRecord,
                    onContentChanged = viewModel::updateDraftContent,
                    onPickImage = { imageLauncher.launch("image/*") },
                    onPickVideo = { videoLauncher.launch("video/*") },
                    onOpenTimeDialog = viewModel::openTimeDialog,
                    onRemoveMedia = viewModel::removeMediaAt,
                )
                BackHandler { viewModel.requestCloseRecordPage() }
            }

            entry<MemoryRoute.Detail> { route ->
                val entry = state.allEntries.firstOrNull { it.id == route.entryId }
                if (entry != null) {
                    DetailScreen(
                        entry = entry,
                        onBack = {
                            viewModel.closeDetailPage()
                            pop()
                        },
                        onEdit = {
                            viewModel.openEditRecordPage(entry)
                            backStack.add(MemoryRoute.Record)
                        },
                        onDelete = {
                            viewModel.deleteEntry(entry)
                            viewModel.closeDetailPage()
                            pop()
                        },
                    )
                } else {
                    LaunchedEffect(route.entryId) {
                        viewModel.closeDetailPage()
                        pop()
                    }
                }
            }

            entry<MemoryRoute.Settings> {
                SettingsScreen(
                    onBack = pop,
                )
            }
        },
    )

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
                    leadingIcon = { Icon(Icons.Default.FileDownload, contentDescription = null) },
                    onClick = {
                        scope.launch { drawerState.close() }
                        onExportAll()
                    },
                )
                DropdownMenuItem(
                    text = { Text("导入数据") },
                    leadingIcon = { Icon(Icons.Default.FileUpload, contentDescription = null) },
                    onClick = {
                        scope.launch { drawerState.close() }
                        onImport()
                    },
                )
                DropdownMenuItem(
                    text = { Text("立即同步") },
                    leadingIcon = { Icon(Icons.Default.FileUpload, contentDescription = null) },
                    onClick = {
                        scope.launch { drawerState.close() }
                        onSyncNow()
                    },
                )
                DropdownMenuItem(
                    text = { Text("设置") },
                    onClick = {
                        scope.launch { drawerState.close() }
                        onOpenSettings()
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
                        text = "待同步媒体 ${state.pendingSyncCount} 项，网络恢复后自动同步",
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
                        Box(modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp)) {
                            MemoryLoadingIndicator(modifier = Modifier.size(26.dp))
                        }
                    },
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(state.timelineEntries, key = { it.id }) { entry ->
                            EntryCard(
                                entry = entry,
                                onOpenDetail = { onOpenDetail(entry) },
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EntryCard(
    entry: LifeEntry,
    onOpenDetail: () -> Unit,
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
                    text = formatDisplayTime(entry.createdAtEpochMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val isCloudSynced = entry.mediaList.isNotEmpty() &&
                    entry.mediaList.all { isCloudSyncedMediaUrl(it.uri) }
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
                        MediaThumb(media = media)
                    }
                }
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(text = { Text("编辑") }, onClick = { menuExpanded = false; onEdit() })
                DropdownMenuItem(text = { Text("删除") }, onClick = { menuExpanded = false; onDelete() })
                DropdownMenuItem(text = { Text("导出这一条") }, onClick = { menuExpanded = false; onExportSingle() })
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
    onEdit: (LifeEntry) -> Unit,
    onDelete: (LifeEntry) -> Unit,
    onExportSingle: (LifeEntry) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    TextField(
                        value = state.searchKeyword,
                        onValueChange = onKeywordChange,
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
                            if (state.searchKeyword.isNotBlank()) {
                                IconButton(onClick = onClear) {
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
                            entry = entry,
                            onOpenDetail = { onOpenDetail(entry) },
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

@Composable
private fun MediaThumb(
    media: LifeMedia,
    onImageClick: (() -> Unit)? = null,
) {
    Box {
        if (media.mimeType?.startsWith("image/") == true) {
            AsyncImage(
                model = media.uri,
                contentDescription = null,
                modifier = Modifier
                    .size(84.dp)
                    .let { base ->
                        if (onImageClick != null) base.clickable(onClick = onImageClick) else base
                    },
            )
        } else {
            Card(modifier = Modifier.size(84.dp), colors = CardDefaults.cardColors()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.PlayCircleFilled, contentDescription = null)
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
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
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
                    TextButton(onClick = onSave) { Text("保存") }
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
                value = state.draftContent,
                onValueChange = onContentChanged,
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

private fun readTextFromUri(context: Context, uri: Uri): String? =
    runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()

private fun writeTextToUri(context: Context, uri: Uri, value: String): Boolean =
    runCatching {
        context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(value) }
        true
    }.getOrDefault(false)

private fun isCloudSyncedMediaUrl(uri: String): Boolean {
    return uri.startsWith("http://") || uri.startsWith("https://")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    onBack: () -> Unit,
) {
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
                headlineContent = { Text("同步策略") },
                supportingContent = { Text("当前：有网络自动同步，可在侧边栏手动立即同步") },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("数据导出/导入") },
                supportingContent = { Text("可在侧边栏导出全部 JSON 或导入 JSON") },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("云端状态标识") },
                supportingContent = { Text("右上角“云端”图标表示媒体地址已是远程链接") },
            )
        }
    }
}
