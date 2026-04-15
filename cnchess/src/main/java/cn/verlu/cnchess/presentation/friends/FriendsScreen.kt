package cn.verlu.cnchess.presentation.friends

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.verlu.cnchess.domain.model.Friendship
import cn.verlu.cnchess.presentation.auth.vm.AuthSessionViewModel
import cn.verlu.cnchess.presentation.navigation.LocalSnackbarHostState
import cn.verlu.cnchess.presentation.ui.CnChessPullToRefreshIndicator
import coil3.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FriendsScreen(
    modifier: Modifier = Modifier,
    viewModel: FriendsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val authState by hiltViewModel<AuthSessionViewModel>().state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val userId = authState.user?.id.orEmpty()
    val snackbar = LocalSnackbarHostState.current
    val pullToRefreshState = rememberPullToRefreshState()

    LaunchedEffect(state.error) {
        val err = state.error ?: return@LaunchedEffect
        snackbar.showSnackbar(err)
        viewModel.clearError()
    }

    state.pendingOutgoingInvite?.let { invite ->
        val peerName = invite.toProfile?.displayName ?: "好友"
        AlertDialog(
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            onDismissRequest = { },
            title = { Text("等待对方接受") },
            text = {
                Text(
                    "已向 $peerName 发送对局邀请。对方接受后将自动进入棋盘。\n\n" +
                        "您可随时取消邀请。",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.cancelInvite(invite.toUserId) },
                ) { Text("取消邀请") }
            },
        )
    }

    // Enter page: empty list uses normal refresh, otherwise silent refresh
    LaunchedEffect(Unit) {
        if (state.friends.isEmpty()) viewModel.refresh() else viewModel.refreshSilently()
    }

    // Back from background/other page: empty list uses normal refresh, otherwise silent refresh
    DisposableEffect(lifecycleOwner, state.friends.isEmpty()) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                if (state.friends.isEmpty()) viewModel.refresh() else viewModel.refreshSilently()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = viewModel::refresh,
        state = pullToRefreshState,
        modifier = modifier.fillMaxSize(),
        indicator = {
            CnChessPullToRefreshIndicator(
                state = pullToRefreshState,
                isRefreshing = state.isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        },
    ) {
        if (state.friends.isEmpty() && !state.isRefreshing) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillParentMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text("暂无好友，请先在 Talk 添加好友")
                    }
                }
            }
            return@PullToRefreshBox
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(state.friends, key = { it.id }) { friendship ->
                val peerId = friendship.peerProfile(userId)?.id.orEmpty()
                FriendInviteItem(
                    friendship = friendship,
                    currentUserId = userId,
                    invited = state.outgoingInviteToUserIds.contains(peerId),
                    waitingInDialog = state.pendingOutgoingInvite?.toUserId == peerId,
                    isOnline = state.onlinePeerUserIds.contains(peerId),
                    isInGame = state.inGamePeerUserIds.contains(peerId),
                    isMyselfInGame = state.isMyselfInGame,
                    onInvite = { id -> viewModel.invite(id) },
                    onCancelInvite = { id -> viewModel.cancelInvite(id) },
                )
                HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
            }
        }
    }
}

@Composable
private fun FriendInviteItem(
    friendship: Friendship,
    currentUserId: String,
    invited: Boolean,
    waitingInDialog: Boolean,
    isOnline: Boolean,
    isInGame: Boolean,
    isMyselfInGame: Boolean,
    onInvite: (String) -> Unit,
    onCancelInvite: (String) -> Unit,
) {
    val peer = friendship.peerProfile(currentUserId)
    val peerId = peer?.id ?: return

    val statusText = when {
        isInGame -> "对局中"
        isOnline -> "在线"
        else -> "离线"
    }
    val statusColor = when {
        isInGame -> Color(0xFFFF9800)
        isOnline -> Color(0xFF2ECC71)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val dotColor = when {
        isInGame -> Color(0xFFFF9800)
        isOnline -> Color(0xFF2ECC71)
        else -> null
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            AsyncImage(
                model = peer.avatarUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
            )
            if (dotColor != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(dotColor),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = peer.displayName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                color = statusColor,
            )
        }
        when {
            !isOnline && !isInGame -> {
                Text(
                    text = "离线中",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            isInGame -> {
                Text(
                    text = "对局中",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFFFF9800),
                )
            }
            isMyselfInGame -> {
                Text(
                    text = "我在对局中",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            invited && waitingInDialog -> {
                Text(
                    text = "等待对方接受…",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            invited -> {
                OutlinedButton(onClick = { onCancelInvite(peerId) }) {
                    Text("取消邀请")
                }
            }
            else -> {
                Button(onClick = { onInvite(peerId) }) {
                    Text("邀请对局")
                }
            }
        }
    }
}
