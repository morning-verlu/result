package cn.verlu.talk.presentation.contacts

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PeopleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.verlu.talk.domain.model.Friendship
import cn.verlu.talk.domain.model.Profile
import cn.verlu.talk.presentation.navigation.LocalSnackbarHostState
import cn.verlu.talk.presentation.ui.TalkLoadingIndicator
import cn.verlu.talk.presentation.ui.TalkPullToRefreshIndicator
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ContactsScreen(
    modifier: Modifier = Modifier,
    onNavigateToChat: (roomId: String) -> Unit = {},
    viewModel: ContactsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pullToRefreshState = rememberPullToRefreshState()
    val snackbar: SnackbarHostState = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        viewModel.navigateToChat.collect { roomId ->
            onNavigateToChat(roomId)
        }
    }

    LaunchedEffect(Unit) {
        if (state.friends.isEmpty()) viewModel.refresh() else viewModel.refreshSilently()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                if (state.friends.isEmpty()) viewModel.refresh() else viewModel.refreshSilently()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 错误提示（如找不到聊天室）
    LaunchedEffect(state.error) {
        val err = state.error ?: return@LaunchedEffect
        scope.launch { snackbar.showSnackbar(err) }
    }

    val pagerState = rememberPagerState(
        initialPage = state.selectedTabIndex.coerceIn(0, 1),
        pageCount = { 2 },
    )

    LaunchedEffect(state.selectedTabIndex) {
        val target = state.selectedTabIndex.coerceIn(0, 1)
        if (pagerState.currentPage != target) {
            pagerState.animateScrollToPage(target)
        }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                if (page != state.selectedTabIndex) {
                    viewModel.selectTab(page)
                }
            }
    }

    Column(modifier = modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = state.selectedTabIndex) {
            Tab(
                selected = state.selectedTabIndex == 0,
                onClick = { viewModel.selectTab(0) },
                text = { Text("好友") },
            )
            Tab(
                selected = state.selectedTabIndex == 1,
                onClick = { viewModel.selectTab(1) },
                text = {
                    val n = state.pendingRequests.size
                    Text(if (n > 0) "新的朋友（$n）" else "新的朋友")
                },
            )
        }
        HorizontalDivider()

        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            state = pullToRefreshState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            indicator = {
                TalkPullToRefreshIndicator(
                    state = pullToRefreshState,
                    isRefreshing = state.isRefreshing,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            },
        ) {
            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 1,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                when (page) {
                    0 -> FriendListTab(
                        friends = state.friends,
                        currentUserId = state.currentUserId,
                        isInitialLoading = state.isInitialLoading,
                        onFriendClick = viewModel::openChatWithPeer,
                    )
                    else -> PendingRequestsTab(
                        requests = state.pendingRequests,
                        currentUserId = state.currentUserId,
                        isInitialLoading = state.isInitialLoading,
                        onAccept = viewModel::acceptRequest,
                        onReject = viewModel::rejectRequest,
                    )
                }
            }
        }
    }

    if (state.showAddFriendDialog) {
        AddFriendDialog(
            query = state.searchQuery,
            onQueryChange = viewModel::setSearchQuery,
            onSearch = viewModel::searchUser,
            isSearching = state.isSearching,
            searchResult = state.searchResult,
            searchError = state.searchError,
            addFriendSuccess = state.addFriendSuccess,
            onSendRequest = viewModel::sendFriendRequest,
            onDismiss = viewModel::hideAddFriendDialog,
        )
    }
}

@Composable
private fun FriendListTab(
    friends: List<Friendship>,
    currentUserId: String,
    isInitialLoading: Boolean,
    onFriendClick: (String) -> Unit,
) {
    if (friends.isEmpty() && isInitialLoading) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item(key = "loading") {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    TalkLoadingIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "正在加载联系人…",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        return
    }

    if (friends.isEmpty()) {
        // 空态也用可滚动容器承载，保证可以下拉刷新
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item(key = "empty") {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        Icons.Default.PeopleOutline,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.outlineVariant,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "还没有好友",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
        return
    }

    val grouped = friends.groupBy { f ->
        val name = f.peerProfile(currentUserId)?.displayName ?: "?"
        name.firstOrNull()?.uppercaseChar()?.toString() ?: "#"
    }.toSortedMap()

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        grouped.forEach { (letter, group) ->
            item(key = "header_$letter") {
                Text(
                    text = letter,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                )
            }
            items(group, key = { it.id }) { friendship ->
                val peer = friendship.peerProfile(currentUserId)
                val peerId = peer?.id
                FriendItem(
                    profile = peer,
                    onClick = {
                        if (peerId != null) onFriendClick(peerId)
                    },
                )
                HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
            }
        }
    }
}

@Composable
private fun PendingRequestsTab(
    requests: List<Friendship>,
    currentUserId: String,
    isInitialLoading: Boolean,
    onAccept: (String) -> Unit,
    onReject: (String) -> Unit,
) {
    if (requests.isEmpty() && isInitialLoading) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item(key = "loading") {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    TalkLoadingIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "正在加载申请…",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        return
    }

    if (requests.isEmpty()) {
        // 空态也用可滚动容器承载，保证可以下拉刷新
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item(key = "empty") {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        "暂无新的好友申请",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(requests, key = { it.id }) { friendship ->
            val requester = friendship.requesterProfile
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(
                    model = requester?.avatarUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = requester?.displayName ?: "未知用户",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = requester?.email ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                OutlinedButton(
                    onClick = { onReject(friendship.id) },
                    modifier = Modifier.padding(end = 6.dp)
                ) { Text("拒绝") }
                Button(onClick = { onAccept(friendship.id) }) { Text("同意") }
            }
            HorizontalDivider(modifier = Modifier.padding(start = 76.dp))
        }
    }
}

@Composable
private fun FriendItem(profile: Profile?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = profile?.avatarUrl,
            contentDescription = null,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = profile?.displayName ?: "未知用户",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            profile?.email?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun AddFriendDialog(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    isSearching: Boolean,
    searchResult: Profile?,
    searchError: String?,
    addFriendSuccess: Boolean,
    onSendRequest: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加好友") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    label = { Text("邮箱") },
                    placeholder = { Text("输入对方注册邮箱") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onSearch,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = query.isNotBlank() && !isSearching && !addFriendSuccess,
                ) {
                    if (isSearching) {
                        TalkLoadingIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text("搜索")
                    }
                }

                searchError?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                if (addFriendSuccess) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("好友申请已发送", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                }

                searchResult?.let { profile ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(
                            model = profile.avatarUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondaryContainer)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(profile.displayName, fontWeight = FontWeight.Medium)
                            profile.email?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { onSendRequest(profile.id) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !addFriendSuccess,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) { Text("申请加为好友") }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}
