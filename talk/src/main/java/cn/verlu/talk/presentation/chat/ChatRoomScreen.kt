package cn.verlu.talk.presentation.chat

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.verlu.talk.domain.model.Message
import cn.verlu.talk.domain.model.MessageType
import cn.verlu.talk.presentation.chat.stickers.LottieStickerView
import cn.verlu.talk.presentation.chat.stickers.StickerPickerSheet
import cn.verlu.talk.presentation.chat.stickers.StickerRegistry
import cn.verlu.talk.presentation.chat.stickers.rememberStickerManifest
import cn.verlu.talk.presentation.chat.voice.VoicePlayer
import cn.verlu.talk.presentation.chat.voice.VoiceRecorder
import cn.verlu.talk.presentation.chat.voice.formatVoiceDuration
import cn.verlu.talk.presentation.chat.voice.parseVoiceContent
import cn.verlu.talk.presentation.navigation.LocalSnackbarHostState
import cn.verlu.talk.util.formatMessageTimestamp
import cn.verlu.talk.util.shouldShowTimeSeparator
import cn.verlu.talk.presentation.ui.TalkLoadingIndicator
import coil3.compose.AsyncImage
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.math.max
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ChatRoomScreen(
    roomId: String,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    viewModel: ChatRoomViewModel = hiltViewModel(key = roomId),
) {
    LaunchedEffect(roomId) { viewModel.init(roomId) }

    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val snackbar = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val stickerManifest by rememberStickerManifest()

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        val pickedUri = uri ?: return@rememberLauncherForActivityResult
        when (val payload = readImagePayload(context, pickedUri)) {
            is ImagePayloadResult.Error -> {
                scope.launch { snackbar.showSnackbar(payload.message) }
            }
            is ImagePayloadResult.Success -> {
                val (bytes, mime, ext) = payload
                viewModel.sendImage(bytes, mime, ext)
            }
        }
    }
    var previewImageIndex by remember { mutableStateOf<Int?>(null) }
    var showStickerPicker by remember { mutableStateOf(false) }

    // 录音相关状态：在 Screen 这层持有，避免重组丢失
    val voiceRecorder = remember { VoiceRecorder(context.applicationContext) }
    DisposableEffect(Unit) {
        onDispose { voiceRecorder.cancel() }
    }
    var voiceState by remember { mutableStateOf<VoiceUiState>(VoiceUiState.Idle) }
    var voiceSendingLocal by remember { mutableStateOf(false) }
    var pendingPermissionAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val recordPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            pendingPermissionAction?.invoke()
        } else {
            scope.launch { snackbar.showSnackbar("需要麦克风权限才能录制语音") }
        }
        pendingPermissionAction = null
    }

    val imeVisible = WindowInsets.ime.getBottom(density) > 0

    val reversedMessages = remember(state.messages) {
        state.messages.asReversed().toList()
    }
    val imageMessages = remember(state.messages) {
        state.messages.filter { it.type == MessageType.IMAGE && !it.isDeleted }
    }

    LaunchedEffect(state.error) {
        val err = state.error ?: return@LaunchedEffect
        snackbar.showSnackbar(err)
    }
    LaunchedEffect(state.isSendingVoice) {
        if (!state.isSendingVoice) voiceSendingLocal = false
    }

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
                    TalkLoadingIndicator(modifier = Modifier.align(Alignment.Center))
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
                                    onImageClick = { clicked ->
                                        val idx = imageMessages.indexOfFirst { it.id == clicked.id }
                                        if (idx >= 0) previewImageIndex = idx
                                    },
                                    stickerManifest = stickerManifest,
                                    pendingVoices = state.pendingVoices,
                                    onRetryVoice = { tempId -> viewModel.retryVoice(tempId) },
                                    onDiscardVoice = { tempId -> viewModel.discardPendingVoice(tempId) },
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
            onPickImage = {
                imagePicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
            onOpenStickers = { showStickerPicker = true },
            voiceState = voiceState,
            onVoiceStart = {
                if (state.isSendingVoice || voiceSendingLocal) return@ChatInputBar
                val ensure: () -> Unit = {
                    if (voiceRecorder.start()) {
                        voiceState = VoiceUiState.Recording(
                            startedAt = System.currentTimeMillis(),
                            pausedAt = null,
                            pausedAccumulatedMs = 0L,
                        )
                    } else {
                        scope.launch { snackbar.showSnackbar("无法开始录音，请检查麦克风占用") }
                        Unit
                    }
                }
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED) {
                    ensure()
                } else {
                    pendingPermissionAction = ensure
                    recordPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
            onVoiceTogglePause = {
                val s = voiceState
                if (s !is VoiceUiState.Recording) return@ChatInputBar
                val now = System.currentTimeMillis()
                if (s.pausedAt == null) {
                    if (voiceRecorder.pause()) {
                        voiceState = s.copy(pausedAt = now)
                    }
                } else {
                    if (voiceRecorder.resume()) {
                        voiceState = s.copy(
                            pausedAt = null,
                            pausedAccumulatedMs = s.pausedAccumulatedMs + (now - s.pausedAt),
                        )
                    }
                }
            },
            onVoiceCancel = {
                voiceRecorder.cancel()
                voiceState = VoiceUiState.Idle
            },
            onVoiceSend = {
                val s = voiceState
                voiceState = VoiceUiState.Idle
                if (s !is VoiceUiState.Recording) {
                    voiceRecorder.cancel()
                    return@ChatInputBar
                }
                val result = voiceRecorder.stop(minDurationMs = 600L)
                if (result == null) {
                    voiceSendingLocal = false
                    scope.launch { snackbar.showSnackbar("说话时间太短") }
                    return@ChatInputBar
                }
                val (bytes, durationMs) = result
                voiceSendingLocal = true
                viewModel.sendVoice(bytes, durationMs)
            },
            isSendingImage = state.isSendingImage,
            isSendingVoice = state.isSendingVoice || voiceSendingLocal,
        )
    }

    previewImageIndex?.let { idx ->
        if (imageMessages.isNotEmpty()) {
            ImagePreviewSheet(
                imageUrls = imageMessages.map { it.content },
                initialIndex = idx.coerceIn(0, imageMessages.lastIndex),
                onDismiss = { previewImageIndex = null },
            )
        } else {
            previewImageIndex = null
        }
    }

    if (showStickerPicker) {
        StickerPickerSheet(
            onDismiss = { showStickerPicker = false },
            onPickSticker = { packId, stickerId ->
                viewModel.sendSticker(packId, stickerId)
                showStickerPicker = false
            },
        )
    }
}

private sealed interface VoiceUiState {
    data object Idle : VoiceUiState
    data class Recording(
        val startedAt: Long,
        val pausedAt: Long?,
        val pausedAccumulatedMs: Long,
    ) : VoiceUiState
}

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
    onImageClick: (Message) -> Unit,
    stickerManifest: cn.verlu.talk.presentation.chat.stickers.StickerPackManifest?,
    pendingVoices: Map<String, PendingVoice> = emptyMap(),
    onRetryVoice: (String) -> Unit = {},
    onDiscardVoice: (String) -> Unit = {},
) {
    val clipboard = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current
    val snackbar = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    var showMenu by remember { mutableStateOf(false) }
    val sharePayload = remember(message.content) { parseCloudShareMessage(message.content) }
    val isSticker = message.type == MessageType.STICKER
    val isVoice = message.type == MessageType.VOICE
    val stickerItem = remember(message.content, stickerManifest) {
        if (isSticker && stickerManifest != null) {
            StickerRegistry.resolve(stickerManifest, message.content)
        } else null
    }

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
            if (isSticker) {
                Box(
                    modifier = Modifier
                        .combinedClickable(
                            onClick = {},
                            onLongClick = { if (!message.isDeleted) showMenu = true }
                        )
                        .padding(4.dp),
                ) {
                    if (stickerItem != null) {
                        LottieStickerView(url = stickerItem.url, size = 120.dp)
                    } else {
                        Text(
                            text = "[表情]",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            } else {
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
                    when {
                        message.type == MessageType.IMAGE -> {
                            AsyncImage(
                                model = message.content,
                                contentDescription = "图片消息",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(190.dp)
                                    .clickable { onImageClick(message) },
                            )
                        }
                        isVoice -> {
                            val pendingTempId = remember(message.content) { parsePendingVoiceTempId(message.content) }
                            val pending = pendingTempId?.let { pendingVoices[it] }
                            VoiceBubbleContent(
                                content = message.content,
                                isMine = isMine,
                                pending = pending,
                                onRetry = { pendingTempId?.let(onRetryVoice) },
                            )
                        }
                        sharePayload != null -> {
                            CloudShareMessageCard(
                                payload = sharePayload,
                                isMine = isMine,
                                onOpenLink = { uriHandler.openUri(sharePayload.url) },
                                onCopyLink = {
                                    clipboard.setText(AnnotatedString(sharePayload.url))
                                    scope.launch { snackbar.showSnackbar("链接已复制") }
                                },
                            )
                        }
                        else -> {
                            Text(
                                text = message.content,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                color = if (isMine) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 15.sp,
                            )
                        }
                    }
                }
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
            ) {
                if (!isSticker && !isVoice) {
                    DropdownMenuItem(
                        text = { Text("复制") },
                        onClick = {
                            clipboard.setText(AnnotatedString(message.content))
                            showMenu = false
                        }
                    )
                }
                if (isMine) {
                    val pendingTempId = if (isVoice) parsePendingVoiceTempId(message.content) else null
                    val isPendingVoice = pendingTempId != null && pendingVoices.containsKey(pendingTempId)
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = if (isPendingVoice) "放弃发送" else "删除",
                                color = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = {
                            if (isPendingVoice && pendingTempId != null) {
                                onDiscardVoice(pendingTempId)
                            } else {
                                onDelete()
                            }
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

@Composable
private fun VoiceBubbleContent(
    content: String,
    isMine: Boolean,
    pending: PendingVoice? = null,
    onRetry: () -> Unit = {},
) {
    val parsed = remember(content) { parseVoiceContent(content) }
    val onColor = if (isMine) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSurfaceVariant
    val playingUrl by VoicePlayer.playingUrl.collectAsStateLocal()
    // pending 语音不允许播放（URL 是 pending:<tempId> 占位）
    val realPlayable = parsed != null && pending == null
    val isPlaying = realPlayable && playingUrl == parsed!!.second
    val durationMs = parsed?.first ?: pending?.durationMs ?: 0L
    val widthDp = (60 + (durationMs / 1000).coerceAtMost(60).toInt() * 3).dp

    Row(
        modifier = Modifier
            .widthIn(min = 100.dp)
            .clickable(enabled = realPlayable || pending?.status == PendingVoice.Status.Failed) {
                when {
                    pending?.status == PendingVoice.Status.Failed -> onRetry()
                    realPlayable -> VoicePlayer.toggle(parsed!!.second)
                }
            }
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .width(widthDp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (pending?.status) {
            PendingVoice.Status.Sending -> {
                TalkLoadingIndicator(modifier = Modifier.size(20.dp))
            }
            PendingVoice.Status.Failed -> {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "重发",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(22.dp),
                )
            }
            null -> {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "暂停" else "播放",
                    tint = onColor,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        // 简易"波形"占位（小圆点条）
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            val dotCount = ((durationMs / 1000).coerceIn(1, 16).toInt()) + 4
            val barColor = when (pending?.status) {
                PendingVoice.Status.Failed -> MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                PendingVoice.Status.Sending -> onColor.copy(alpha = 0.4f)
                null -> onColor.copy(alpha = 0.7f)
            }
            repeat(dotCount) {
                Box(
                    modifier = Modifier
                        .size(width = 2.dp, height = if (it % 3 == 0) 14.dp else 8.dp)
                        .background(barColor, shape = RoundedCornerShape(1.dp)),
                )
            }
        }
        Text(
            text = when (pending?.status) {
                PendingVoice.Status.Sending -> "发送中"
                PendingVoice.Status.Failed -> "重发"
                null -> formatVoiceDuration(durationMs)
            },
            color = if (pending?.status == PendingVoice.Status.Failed) MaterialTheme.colorScheme.error else onColor,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun <T> kotlinx.coroutines.flow.StateFlow<T>.collectAsStateLocal(): androidx.compose.runtime.State<T> =
    this.collectAsState()

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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CloudShareMessageCard(
    payload: CloudSharePayload,
    isMine: Boolean,
    onOpenLink: () -> Unit,
    onCopyLink: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val linkColor = if (isMine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary

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
            style = MaterialTheme.typography.bodySmall.copy(textDecoration = TextDecoration.Underline),
            maxLines = if (expanded) Int.MAX_VALUE else 2,
            overflow = if (expanded) TextOverflow.Clip else TextOverflow.Ellipsis,
            color = linkColor,
            modifier = Modifier.combinedClickable(
                onClick = onOpenLink,
                onLongClick = onCopyLink,
            ),
        )
        if (payload.url.length > 60) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (expanded) "收起" else "查看更多",
                style = MaterialTheme.typography.labelSmall,
                color = linkColor.copy(alpha = 0.8f),
                modifier = Modifier.clickable { expanded = !expanded },
            )
        }
    }
}

/**
 * WhatsApp 风格输入栏：
 *  - 左侧表情按钮、文本框
 *  - 文本为空：右侧显示「图片」按钮 + 主色「麦克风」按钮（长按录音）
 *  - 文本非空：右侧仅显示「发送」按钮
 *  - 录音中：整条输入栏切换成红色波形 + 计时 + 「上滑取消」提示
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun ChatInputBar(
    modifier: Modifier = Modifier,
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onPickImage: () -> Unit,
    onOpenStickers: () -> Unit,
    voiceState: VoiceUiState,
    onVoiceStart: () -> Unit,
    onVoiceTogglePause: () -> Unit,
    onVoiceCancel: () -> Unit,
    onVoiceSend: () -> Unit,
    isSendingImage: Boolean,
    isSendingVoice: Boolean,
) {
    val hasText = text.isNotBlank()
    val isRecording = voiceState is VoiceUiState.Recording

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 6.dp, vertical = 6.dp),
        ) {
            // 普通输入态
            AnimatedVisibility(
                visible = !isRecording,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Surface(
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(24.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 44.dp)
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(
                                onClick = onOpenStickers,
                                modifier = Modifier.size(40.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.EmojiEmotions,
                                    contentDescription = "表情",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(26.dp),
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 4.dp, vertical = 8.dp),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                if (text.isEmpty()) {
                                    Text(
                                        text = "输入消息",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        fontSize = 15.sp,
                                    )
                                }
                                BasicTextField(
                                    value = text,
                                    onValueChange = onTextChange,
                                    modifier = Modifier.fillMaxWidth(),
                                    textStyle = LocalTextStyle.current.copy(
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 15.sp,
                                    ),
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    maxLines = 5,
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    AnimatedContent(
                        targetState = hasText,
                        transitionSpec = {
                            (scaleIn(animationSpec = tween(180)) + fadeIn(animationSpec = tween(180)))
                                .togetherWith(scaleOut(animationSpec = tween(120)) + fadeOut(animationSpec = tween(120)))
                        },
                        label = "input-action",
                    ) { showSend ->
                        if (showSend) {
                            PrimaryRoundButton(onClick = onSend, contentDescription = "发送") {
                                Icon(
                                    Icons.AutoMirrored.Filled.Send,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SecondaryRoundButton(
                                    onClick = onPickImage,
                                    enabled = !isSendingImage,
                                    contentDescription = "图片",
                                ) {
                                    if (isSendingImage) {
                                        TalkLoadingIndicator(modifier = Modifier.size(22.dp))
                                    } else {
                                        Icon(
                                            Icons.Default.PhotoLibrary,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(22.dp),
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                PrimaryRoundButton(
                                    onClick = onVoiceStart,
                                    contentDescription = "语音",
                                ) {
                                    if (isSendingVoice) {
                                        TalkLoadingIndicator(modifier = Modifier.size(22.dp))
                                    } else {
                                        Icon(
                                            Icons.Default.Mic,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(22.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 录音态
            AnimatedVisibility(
                visible = isRecording,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                RecordingBar(
                    state = voiceState as? VoiceUiState.Recording,
                    onTogglePause = onVoiceTogglePause,
                    onCancel = onVoiceCancel,
                    onSend = onVoiceSend,
                    sending = isSendingVoice,
                )
            }
        }
    }
}

@Composable
private fun RecordingBar(
    state: VoiceUiState.Recording?,
    onTogglePause: () -> Unit,
    onCancel: () -> Unit,
    onSend: () -> Unit,
    sending: Boolean,
) {
    if (state == null) return
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.startedAt) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(100)
        }
    }
    val pausedCarry = if (state.pausedAt != null) nowMs - state.pausedAt else 0L
    val elapsedMs = (nowMs - state.startedAt - state.pausedAccumulatedMs - pausedCarry).coerceAtLeast(0L)
    val isPaused = state.pausedAt != null

    val infinite = rememberInfiniteTransition(label = "rec-pulse")
    val pulse by infinite.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(24.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error.copy(alpha = pulse)),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = formatRecordingDuration(elapsedMs),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.weight(1f))
            SecondaryRoundButton(onClick = onCancel, contentDescription = "删除") {
                Icon(
                    Icons.Default.Close,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            SecondaryRoundButton(onClick = onTogglePause, contentDescription = "暂停或播放") {
                Icon(
                    imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            PrimaryRoundButton(onClick = onSend, contentDescription = "发送语音") {
                if (sending) {
                    TalkLoadingIndicator(modifier = Modifier.size(20.dp))
                } else {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

private fun formatRecordingDuration(elapsedMs: Long): String {
    val totalSec = (elapsedMs / 1000).toInt()
    val mm = totalSec / 60
    val ss = totalSec % 60
    return "%02d:%02d".format(mm, ss)
}

@Composable
private fun PrimaryRoundButton(
    onClick: () -> Unit,
    contentDescription: String,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.size(44.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        onClick = onClick,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

@Composable
private fun SecondaryRoundButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    contentDescription: String,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.size(44.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        onClick = onClick,
        enabled = enabled,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

private sealed interface ImagePayloadResult {
    data class Success(
        val bytes: ByteArray,
        val mimeType: String,
        val extension: String,
    ) : ImagePayloadResult

    data class Error(val message: String) : ImagePayloadResult
}

private fun readImagePayload(
    context: Context,
    uri: Uri,
    maxBytes: Int = 5 * 1024 * 1024,
): ImagePayloadResult {
    val resolver = context.contentResolver
    val mimeType = resolver.getType(uri)?.takeIf { it.startsWith("image/") } ?: "image/jpeg"
    val ext = when {
        mimeType.contains("png") -> "png"
        mimeType.contains("webp") -> "webp"
        mimeType.contains("gif") -> "gif"
        else -> "jpg"
    }
    return runCatching {
        val rawBytes = readBytesCapped(context, uri, capBytes = 24 * 1024 * 1024)
            ?: return ImagePayloadResult.Error("图片过大或读取失败")
        if (rawBytes.isEmpty()) return ImagePayloadResult.Error("读取图片失败")
        if (rawBytes.size <= maxBytes) {
            return ImagePayloadResult.Success(bytes = rawBytes, mimeType = mimeType, extension = ext)
        }
        val compressed = compressImageToJpegUnderLimit(context, uri, maxBytes)
            ?: return ImagePayloadResult.Error("图片过大，压缩后仍超过 5MB")
        ImagePayloadResult.Success(bytes = compressed, mimeType = "image/jpeg", extension = "jpg")
    }.getOrElse { e ->
        android.util.Log.e("Talk/ChatRoom", "readImagePayload failed", e)
        ImagePayloadResult.Error("图片读取失败，请重试")
    }
}

private fun readBytesCapped(
    context: Context,
    uri: Uri,
    capBytes: Int,
): ByteArray? {
    val resolver = context.contentResolver
    return resolver.openInputStream(uri)?.use { input ->
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            total += read
            if (total > capBytes) return null
            out.write(buffer, 0, read)
        }
        out.toByteArray()
    }
}

private fun compressImageToJpegUnderLimit(
    context: Context,
    uri: Uri,
    maxBytes: Int,
): ByteArray? {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    val maxSide = max(bounds.outWidth, bounds.outHeight)
    var sampleSize = 1
    while ((maxSide / sampleSize) > 2560) sampleSize *= 2

    val decoded = resolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(
            it,
            null,
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            },
        )
    } ?: return null

    var working = decoded
    val qualityCandidates = intArrayOf(92, 84, 76, 68, 60, 52, 44, 36, 28)
    repeat(5) {
        for (quality in qualityCandidates) {
            val out = ByteArrayOutputStream()
            working.compress(Bitmap.CompressFormat.JPEG, quality, out)
            val bytes = out.toByteArray()
            if (bytes.size <= maxBytes) {
                if (working !== decoded) working.recycle()
                decoded.recycle()
                return bytes
            }
        }
        val nextW = (working.width * 0.82f).toInt().coerceAtLeast(320)
        val nextH = (working.height * 0.82f).toInt().coerceAtLeast(320)
        if (nextW >= working.width || nextH >= working.height) return@repeat
        val scaled = Bitmap.createScaledBitmap(working, nextW, nextH, true)
        if (working !== decoded) working.recycle()
        working = scaled
    }
    if (working !== decoded) working.recycle()
    decoded.recycle()
    return null
}
