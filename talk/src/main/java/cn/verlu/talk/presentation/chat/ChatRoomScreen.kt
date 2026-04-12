package cn.verlu.talk.presentation.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.verlu.talk.domain.model.Message
import cn.verlu.talk.presentation.navigation.LocalSnackbarHostState
import cn.verlu.talk.util.formatMessageTimestamp
import cn.verlu.talk.util.shouldShowTimeSeparator
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChatRoomScreen(
    roomId: String,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    viewModel: ChatRoomViewModel = hiltViewModel(),
) {
    LaunchedEffect(roomId) { viewModel.init(roomId) }

    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val snackbar = LocalSnackbarHostState.current
    val density = LocalDensity.current

    // `First item` in a reverseLayout LazyColumn is anchored to the bottom (input side),
    // so the timeline opens on the latest messages with **no** scroll-from-top animation.
    // `WindowInsets.ime` is @Composable; read it here (not inside derivedStateOf).
    val imeVisible = WindowInsets.ime.getBottom(density) > 0

    val reversedMessages = remember(state.messages) {
        state.messages.asReversed().toList()
    }

    // 错误提示
    LaunchedEffect(state.error) {
        val err = state.error ?: return@LaunchedEffect
        snackbar.showSnackbar(err)
    }

    // Keyboard open: instant snap so the newest messages stay above the IME (no long animation).
    LaunchedEffect(imeVisible) {
        if (imeVisible && reversedMessages.isNotEmpty()) {
            listState.scrollToItem(0)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
    ) {
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (state.peerProfile?.avatarUrl != null) {
                        AsyncImage(
                            model = state.peerProfile?.avatarUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondaryContainer)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = state.peerProfile?.displayName ?: "聊天",
                        fontWeight = FontWeight.Medium,
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            }
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when {
                state.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                state.messages.isEmpty() -> {
                    Text(
                        text = "还没有消息，发送第一条吧",
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                else -> {
                    LazyColumn(
                        state = listState,
                        reverseLayout = true,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        item { Spacer(modifier = Modifier.height(4.dp)) }
                        itemsIndexed(
                            items = reversedMessages,
                            key = { _, message -> message.id },
                        ) { index, message ->
                            val older = reversedMessages.getOrNull(index + 1)
                            // In reverseLayout the first composable within an item renders closer
                            // to the input bar (visually lower). Put the bubble first so it sits
                            // nearest the input, and the time separator second so it appears above.
                            if (message.isDeleted) {
                                RevokedMessageLine(
                                    message = message,
                                    currentUserId = state.currentUserId,
                                )
                            } else {
                                MessageBubble(
                                    message = message,
                                    isMine = message.senderId == state.currentUserId,
                                    onDelete = { viewModel.deleteMessage(message.id) },
                                )
                            }
                            if (shouldShowTimeSeparator(message.createdAtMs, older?.createdAtMs)) {
                                TimeSeparator(epochMs = message.createdAtMs)
                            }
                        }
                    }
                }
            }
        }
        ChatInputBar(
            modifier = Modifier.fillMaxWidth(),
            text = state.inputText,
            onTextChange = viewModel::onInputChange,
            onSend = viewModel::sendMessage,
        )
    }
}

/** 软删除（撤回）：双方会话内居中提示；依赖 Realtime UPDATE 与含 tombstone 的同步让对方也能看到。 */
@Composable
private fun RevokedMessageLine(
    message: Message,
    currentUserId: String,
) {
    val label = if (message.senderId == currentUserId) {
        "你撤回了一条消息"
    } else {
        val name = message.senderProfile?.displayName?.takeIf { it.isNotBlank() } ?: "对方"
        "$name 撤回了一条消息"
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun TimeSeparator(epochMs: Long) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = formatMessageTimestamp(epochMs),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: Message,
    isMine: Boolean,
    onDelete: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current
    var showMenu by remember { mutableStateOf(false) }
    val sharePayload = remember(message.content) { parseCloudShareMessage(message.content) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (!isMine) {
            AsyncImage(
                model = message.senderProfile?.avatarUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer)
            )
            Spacer(modifier = Modifier.width(6.dp))
        }

        Box {
            Surface(
                shape = RoundedCornerShape(
                    topStart = if (isMine) 16.dp else 4.dp,
                    topEnd = if (isMine) 4.dp else 16.dp,
                    bottomStart = 16.dp,
                    bottomEnd = 16.dp,
                ),
                color = if (isMine) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .widthIn(max = 260.dp)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = { if (!message.isDeleted) showMenu = true }
                    )
            ) {
                if (sharePayload != null) {
                    CloudShareMessageCard(
                        payload = sharePayload,
                        isMine = isMine,
                        onOpenLink = { uriHandler.openUri(sharePayload.url) },
                        onCopyLink = { clipboard.setText(AnnotatedString(sharePayload.url)) },
                    )
                } else {
                    Text(
                        text = message.content,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        color = if (isMine) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 15.sp,
                    )
                }
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
            ) {
                DropdownMenuItem(
                    text = { Text("复制") },
                    onClick = {
                        clipboard.setText(AnnotatedString(message.content))
                        showMenu = false
                    }
                )
                if (isMine) {
                    DropdownMenuItem(
                        text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            onDelete()
                            showMenu = false
                        }
                    )
                }
            }
        }

        if (isMine) {
            Spacer(modifier = Modifier.width(6.dp))
        }
    }
}

private data class CloudSharePayload(
    val fileName: String?,
    val fileSize: String?,
    val url: String,
)

private fun parseCloudShareMessage(content: String): CloudSharePayload? {
    if (!content.contains("下载链接：")) return null
    val lines = content.lines()
    val fileName = lines.firstOrNull { it.startsWith("文件名：") }?.removePrefix("文件名：")?.trim()
    val fileSize = lines.firstOrNull { it.startsWith("大小：") }?.removePrefix("大小：")?.trim()
    val urlLine = lines.firstOrNull { it.startsWith("下载链接：") } ?: return null
    val url = urlLine.removePrefix("下载链接：").trim()
    if (!url.startsWith("http://") && !url.startsWith("https://")) return null
    return CloudSharePayload(fileName = fileName, fileSize = fileSize, url = url)
}

@Composable
private fun CloudShareMessageCard(
    payload: CloudSharePayload,
    isMine: Boolean,
    onOpenLink: () -> Unit,
    onCopyLink: () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
        Text(
            text = "文件分享",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (isMine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        )
        payload.fileName?.let {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isMine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            )
        }
        payload.fileSize?.let {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = if (isMine) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = payload.url,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = if (isMine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable(onClick = onOpenLink),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCopyLink) { Text("复制链接") }
            OutlinedButton(onClick = onOpenLink) { Text("打开链接") }
        }
    }
}

@Composable
private fun ChatInputBar(
    modifier: Modifier = Modifier,
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .imePadding()
            ,
        verticalAlignment = Alignment.Bottom,
    ) {

        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("发送消息") },
            shape = MaterialTheme.shapes.medium,
            maxLines = 4,
        )
        IconButton(onClick = { /* 更多操作 TODO */ }) {
            Icon(Icons.Default.Add, contentDescription = "更多")
        }
        IconButton(
            onClick = onSend,
            enabled = text.isNotBlank(),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Send,
                contentDescription = "发送",
                tint = if (text.isNotBlank()) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline,
            )
        }
    }
}
