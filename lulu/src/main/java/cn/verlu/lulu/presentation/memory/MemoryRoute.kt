package cn.verlu.lulu.presentation.memory

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.verlu.lulu.domain.memory.Memory
import cn.verlu.lulu.domain.memory.MemorySyncStatus
import cn.verlu.lulu.domain.memory.MemorySyncSummary
import cn.verlu.lulu.presentation.memory.MemoryContract.MemoryTimeFilter
import cn.verlu.lulu.presentation.ui.SyncLoadingIndicator
import cn.verlu.lulu.presentation.ui.SyncPullToRefreshIndicator
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.collectLatest

@Composable
fun MemoryRoute(
    onMemoryClick: (String) -> Unit,
    onOpenSearch: () -> Unit,
    setTopBarActions: ((@Composable RowScope.() -> Unit)?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MemoryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.effects.collectLatest { effect ->
            if (effect is MemoryContract.Effect.ShowMessage) {
                snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    LaunchedEffect(state.syncSummary.isSyncing) {
        setTopBarActions {
            IconButton(
                onClick = { viewModel.onIntent(MemoryContract.Intent.Refresh) },
                enabled = !state.syncSummary.isSyncing,
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "同步",
                )
            }
            IconButton(
                onClick = onOpenSearch,
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "搜索",
                )
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { setTopBarActions(null) }
    }

    Box(modifier = modifier.fillMaxSize()) {
        MemoryContent(
            state = state,
            onRetry = { viewModel.onIntent(MemoryContract.Intent.Retry) },
            onRefresh = { viewModel.onIntent(MemoryContract.Intent.Refresh) },
            onRetrySync = { viewModel.onIntent(MemoryContract.Intent.RetrySync) },
            onMemoryClick = onMemoryClick,
            modifier = Modifier.fillMaxSize(),
        )
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun MemorySearchRoute(
    onMemoryClick: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MemoryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }
    var queryValue by remember { mutableStateOf(TextFieldValue(state.searchQuery)) }

    LaunchedEffect(state.searchQuery) {
        if (state.searchQuery != queryValue.text) {
            queryValue = TextFieldValue(
                text = state.searchQuery,
                selection = TextRange(state.searchQuery.length),
            )
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    OutlinedTextField(
                        value = queryValue,
                        onValueChange = { value ->
                            queryValue = value
                            viewModel.onIntent(MemoryContract.Intent.SearchChanged(value.text))
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        placeholder = { Text("搜索记忆") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (queryValue.text.isNotBlank()) {
                                IconButton(
                                    onClick = {
                                        queryValue = TextFieldValue("")
                                        viewModel.onIntent(MemoryContract.Intent.SearchChanged(""))
                                    },
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "清空")
                                }
                            }
                        },
                        singleLine = true,
                        maxLines = 1,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
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
    ) { innerPadding ->
        MemorySearchContent(
            state = state,
            onTimeFilterChanged = { viewModel.onIntent(MemoryContract.Intent.TimeFilterChanged(it)) },
            onClearFilters = { viewModel.onIntent(MemoryContract.Intent.ClearFilters) },
            onMemoryClick = onMemoryClick,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryCreateRoute(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MemoryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.onIntent(MemoryContract.Intent.StartCreate)
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is MemoryContract.Effect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
                MemoryContract.Effect.MemorySaved -> onSaved()
                MemoryContract.Effect.MemoryDeleted -> Unit
            }
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("记录") },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            viewModel.onIntent(MemoryContract.Intent.CancelCreate)
                            onBack()
                        },
                        enabled = !state.isSaving,
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "取消")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.onIntent(MemoryContract.Intent.SaveMemory) },
                        enabled = state.canSave,
                    ) {
                        if (state.isSaving) {
                            SyncLoadingIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        } else {
                            Text("保存")
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        MemoryRecordEditor(
            state = state,
            onContentChanged = { viewModel.onIntent(MemoryContract.Intent.ContentChanged(it)) },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        )
    }
}

@Composable
fun MemoryDetailRoute(
    memoryId: String,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MemoryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(memoryId) {
        viewModel.onIntent(MemoryContract.Intent.LoadDetail(memoryId))
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is MemoryContract.Effect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
                MemoryContract.Effect.MemorySaved -> Unit
                MemoryContract.Effect.MemoryDeleted -> onDeleted()
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { if (!state.isDeleting) showDeleteConfirm = false },
            title = { Text("删除这条记忆？") },
            text = { Text("删除会先在本地隐藏，已同步的记忆会在后台同步为云端软删除。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.onIntent(MemoryContract.Intent.DeleteSelectedMemory)
                    },
                    enabled = !state.isDeleting,
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }, enabled = !state.isDeleting) {
                    Text("取消")
                }
            },
        )
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        if (state.isEditing) {
            MemoryEditor(
                state = state,
                title = "编辑记忆",
                primaryText = if (state.isSaving) "保存中" else "保存修改",
                onBack = { viewModel.onIntent(MemoryContract.Intent.CancelEdit) },
                onSave = { viewModel.onIntent(MemoryContract.Intent.SaveSelectedMemory) },
                onContentChanged = { viewModel.onIntent(MemoryContract.Intent.ContentChanged(it)) },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
        } else {
            MemoryDetailContent(
                state = state,
                onBack = onBack,
                onEditClick = { viewModel.onIntent(MemoryContract.Intent.StartEdit) },
                onDeleteClick = { showDeleteConfirm = true },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemoryContent(
    state: MemoryContract.UiState,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    onRetrySync: () -> Unit,
    onMemoryClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pullToRefreshState = rememberPullToRefreshState()
    val timelineGroups = remember(state.memories) {
        state.memories
            .groupBy { it.createdAt.atZone(ZoneId.systemDefault()).toLocalDate() }
            .toList()
            .sortedByDescending { it.first }
    }

    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        state = pullToRefreshState,
        modifier = modifier,
        indicator = {
            SyncPullToRefreshIndicator(
                state = pullToRefreshState,
                isRefreshing = state.isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        },
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, top = 10.dp, end = 20.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                state.isLoading -> item(key = "loading") {
                    CenterState {
                        SyncLoadingIndicator()
                        Text("正在读取本地记忆", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                state.errorMessage != null -> item(key = "error") {
                    ErrorState(message = state.errorMessage, onRetry = onRetry)
                }

                state.memories.isEmpty() -> item(key = "empty") {
                    EmptyState(hasActiveFilters = false, onClearFilters = {})
                }

                else -> {
                    item(key = "syncSummary") {
                        MemorySyncStrip(
                            summary = state.syncSummary,
                            onRetrySync = onRetrySync,
                        )
                    }
                    timelineGroups.forEach { (date, memories) ->
                        item(key = "day-$date") {
                            TimelineDayHeader(date = date)
                        }
                        items(items = memories, key = { it.id }) { memory ->
                            MemoryCard(
                                memory = memory,
                                showCloudBadge = state.showCloudBadge,
                                onClick = { onMemoryClick(memory.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MemorySyncStrip(
    summary: MemorySyncSummary,
    onRetrySync: () -> Unit,
) {
    if (!summary.canRetry && !summary.isSyncing && summary.lastError == null) return
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = summary.memorySyncText(),
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        IconButton(
            onClick = onRetrySync,
            enabled = !summary.isSyncing,
        ) {
            Icon(Icons.Default.Refresh, contentDescription = "同步")
        }
    }
}

@Composable
private fun TimelineDayHeader(date: LocalDate) {
    Text(
        text = date.timelineTitle(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun MemorySearchContent(
    state: MemoryContract.UiState,
    onTimeFilterChanged: (MemoryTimeFilter) -> Unit,
    onClearFilters: () -> Unit,
    onMemoryClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, top = 10.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item("timeFilters") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(MemoryTimeFilter.entries) { filter ->
                    FilterChip(
                        selected = state.timeFilter == filter,
                        onClick = { onTimeFilterChanged(filter) },
                        label = { Text(filter.label) },
                    )
                }
                if (state.hasActiveFilters) {
                    item {
                        AssistChip(
                            onClick = onClearFilters,
                            label = { Text("清除筛选") },
                            leadingIcon = { Icon(Icons.Default.Close, contentDescription = null) },
                        )
                    }
                }
            }
        }
        item("summary") {
            Text(
                text = "共 ${state.memories.size} 条结果",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (state.memories.isEmpty()) {
            item("searchEmpty") {
                EmptyState(
                    hasActiveFilters = state.hasActiveFilters,
                    onClearFilters = onClearFilters,
                )
            }
        } else {
            items(items = state.memories, key = { it.id }) { memory ->
                MemoryCard(
                    memory = memory,
                    showCloudBadge = state.showCloudBadge,
                    onClick = { onMemoryClick(memory.id) },
                )
            }
        }
    }
}

@Composable
private fun MemoryRecordEditor(
    state: MemoryContract.UiState,
    onContentChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
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

    Column(
        modifier = modifier
            .imePadding()
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
            enabled = !state.isSaving,
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
    }
}

@Composable
private fun MemoryEditor(
    state: MemoryContract.UiState,
    title: String,
    primaryText: String,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onContentChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.imePadding(),
        contentPadding = PaddingValues(start = 24.dp, top = 12.dp, end = 24.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack, enabled = !state.isSaving) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Button(onClick = onSave, enabled = state.canSave) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Text(primaryText, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
        item {
            TextField(
                value = state.draftContent,
                onValueChange = onContentChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 360.dp),
                enabled = !state.isSaving,
                placeholder = { Text("今天在想什么...") },
                minLines = 14,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    disabledContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    disabledIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
            )
        }
    }
}

@Composable
private fun MemoryCard(
    memory: Memory,
    showCloudBadge: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = memory.createdAt.memoryTime(),
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                MemoryCloudBadge(memory.syncStatus, showCloudBadge)
            }
            Text(
                text = memory.content,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
            )
            MemoryMeta(memory = memory)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MemoryMeta(memory: Memory) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (memory.tags.isNotEmpty()) {
            Text(
                text = memory.tags.joinToString(prefix = "#", separator = " #"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MemoryCloudBadge(
    syncStatus: MemorySyncStatus,
    showCloudBadge: Boolean = true,
) {
    if (!showCloudBadge || syncStatus != MemorySyncStatus.SYNCED) return
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.CloudDone,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = "云端",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun MemoryDetailContent(
    state: MemoryContract.UiState,
    onBack: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        state.isDetailLoading -> CenterState(modifier = modifier) {
            SyncLoadingIndicator()
            Text("正在读取记忆", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        state.detailErrorMessage != null -> ErrorState(
            message = state.detailErrorMessage,
            onRetry = onBack,
            modifier = modifier,
        )

        state.selectedMemory != null -> MemoryDetail(
            memory = state.selectedMemory,
            isDeleting = state.isDeleting,
            onBack = onBack,
            onEditClick = onEditClick,
            onDeleteClick = onDeleteClick,
            modifier = modifier,
        )
    }
}

@Composable
private fun MemoryDetail(
    memory: Memory,
    isDeleting: Boolean,
    onBack: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 24.dp, top = 12.dp, end = 24.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, enabled = !isDeleting) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onEditClick, enabled = !isDeleting) {
                    Icon(Icons.Default.Edit, contentDescription = "编辑")
                }
                IconButton(onClick = onDeleteClick, enabled = !isDeleting) {
                    Icon(Icons.Default.Delete, contentDescription = "删除")
                }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatCreatedAt(memory, includeYear = true),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                MemoryCloudBadge(memory.syncStatus)
            }
        }
        item {
            Text(
                text = memory.content,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        if (memory.tags.isNotEmpty()) {
            item {
                DetailTags(tags = memory.tags)
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailMetaRow(label = "创建时间", value = formatCreatedAt(memory, includeYear = true))
                DetailMetaRow(label = "更新时间", value = formatUpdatedAt(memory))
                DetailMetaRow(label = "存储策略", value = if (memory.localOnly) "本地优先" else "云端增强")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailTags(tags: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "标签",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            tags.forEach { tag ->
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            text = tag,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun DetailMetaRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Text(
            value,
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun EmptyState(
    hasActiveFilters: Boolean,
    onClearFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CenterState(modifier = modifier) {
        Text(
            text = if (hasActiveFilters) "没有匹配的记忆" else "还没有记忆",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = if (hasActiveFilters) "换个关键词、类型或时间再找找。" else "点击右下角按钮，先把一条本地记忆写下来。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (hasActiveFilters) {
            OutlinedButton(onClick = onClearFilters) {
                Icon(Icons.Default.Close, contentDescription = null)
                Text("清除筛选", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CenterState(modifier = modifier) {
        Text("记忆加载失败", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = onRetry) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Text("重试", modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun CenterState(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(320.dp)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

private val MemorySyncStatus.label: String
    get() = when (this) {
        MemorySyncStatus.LOCAL_ONLY -> "仅本地"
        MemorySyncStatus.PENDING -> "待同步"
        MemorySyncStatus.SYNCED -> "已同步"
        MemorySyncStatus.FAILED -> "同步失败"
    }

private fun MemorySyncSummary.memorySyncText(): String = when {
    isSyncing -> "正在同步记忆"
    failedCount > 0 -> "$failedCount 条同步失败，可手动重试"
    pendingCount > 0 -> "$pendingCount 条等待同步"
    localOnlyCount > 0 -> "$localOnlyCount 条仅保存在本地，登录后会自动补同步"
    syncedCount > 0 -> "已同步 $syncedCount 条记忆"
    else -> "本地优先保存，登录后自动同步到云端。"
}

private val MemorySyncSummary.canRetry: Boolean
    get() = failedCount > 0 || pendingCount > 0 || lastError != null

private val MemoryTimeFilter.label: String
    get() = when (this) {
        MemoryTimeFilter.All -> "全部时间"
        MemoryTimeFilter.Today -> "今天"
        MemoryTimeFilter.ThisWeek -> "近 7 天"
        MemoryTimeFilter.ThisMonth -> "本月"
    }

private fun formatCreatedAt(
    memory: Memory,
    includeYear: Boolean = false,
): String {
    val pattern = if (includeYear) "yyyy年M月d日 HH:mm" else "M月d日 HH:mm"
    return memory.createdAt
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern(pattern))
}

private fun formatUpdatedAt(memory: Memory): String =
    memory.updatedAt
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm"))

private fun java.time.Instant.memoryTime(): String =
    atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))

private fun LocalDate.timelineTitle(): String {
    val today = LocalDate.now(ZoneId.systemDefault())
    return when (this) {
        today -> "今天"
        today.minusDays(1) -> "昨天"
        else -> format(DateTimeFormatter.ofPattern("M月d日 EEEE"))
    }
}
