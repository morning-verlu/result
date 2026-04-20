package cn.verlu.talk.presentation.chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.verlu.talk.data.remote.dto.FriendshipDto
import cn.verlu.talk.data.remote.dto.ProfileDto
import cn.verlu.talk.data.remote.dto.toDomain
import cn.verlu.talk.data.repository.MessageRepository
import cn.verlu.talk.di.IoDispatcher
import cn.verlu.talk.domain.model.Message
import cn.verlu.talk.domain.model.MessageType
import cn.verlu.talk.domain.model.Profile
import cn.verlu.talk.presentation.chat.stickers.StickerRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val TAG = "Talk/ChatRoomVM"

data class ChatRoomState(
    val messages: List<Message> = emptyList(),
    val inputText: String = "",
    val peerProfile: Profile? = null,
    val currentUserId: String = "",
    val isLoading: Boolean = false,
    val isSendingImage: Boolean = false,
    val isSendingVoice: Boolean = false,
    val error: String? = null,
    /**
     * 发送中/失败的语音消息（key = 乐观消息的 tempId，即 Message.id）。
     * - status = "sending"：气泡显示 loading；发送成功后会被移除并由服务端消息替换
     * - status = "failed"：气泡显示红色「点击重发」；点击后走 retryVoice()
     */
    val pendingVoices: Map<String, PendingVoice> = emptyMap(),
)

/** 本地待发/重发语音的元数据。bytes 常驻直到发送成功。 */
data class PendingVoice(
    val bytes: ByteArray,
    val durationMs: Long,
    val status: Status = Status.Sending,
) {
    enum class Status { Sending, Failed }
}

/** 乐观语音消息的本地内容协议：`<durationMs>|pending:<tempId>`。 */
internal fun encodePendingVoiceContent(durationMs: Long, tempId: String): String =
    "$durationMs|pending:$tempId"

fun parsePendingVoiceTempId(content: String): String? {
    val idx = content.indexOf('|')
    if (idx <= 0) return null
    val tail = content.substring(idx + 1)
    return if (tail.startsWith("pending:")) tail.removePrefix("pending:") else null
}

@HiltViewModel
class ChatRoomViewModel @Inject constructor(
    private val messageRepository: MessageRepository,
    private val supabase: SupabaseClient,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _state = MutableStateFlow(ChatRoomState())
    val state: StateFlow<ChatRoomState> = _state.asStateFlow()

    private var currentRoomId: String = ""

    fun init(roomId: String) {
        if (currentRoomId == roomId) return
        currentRoomId = roomId
        _state.update { it.copy(currentUserId = supabase.auth.currentUserOrNull()?.id ?: "") }

        // Observe Room cache (instant)
        messageRepository.observeMessages(roomId)
            .onEach { msgs ->
                _state.update { s ->
                    val currentUserId = s.currentUserId
                    // Keep only optimistic messages that have NOT yet been confirmed by the server.
                    // Match by content + sender, using a 30-second window to allow for identical
                    // messages sent at different times to coexist as separate optimistic entries.
                    val optimistic = s.messages.filter { it.id.startsWith("optimistic_") }
                    if (optimistic.isEmpty()) {
                        return@update s.copy(messages = msgs, isLoading = msgs.isEmpty() && s.isLoading)
                    }
                    val recentRealThresholdMs = System.currentTimeMillis() - 30_000L
                    val recentRealContents = msgs
                        .filter { it.senderId == currentUserId && it.createdAtMs >= recentRealThresholdMs }
                        .map { it.content }
                        .toMutableList()
                    // For each optimistic message, try to "consume" a matching real message once.
                    val remainingOptimistic = optimistic.filter { opt ->
                        val idx = recentRealContents.indexOf(opt.content)
                        if (idx >= 0) {
                            recentRealContents.removeAt(idx) // consume this match; don't dedup twice
                            false // remove optimistic (real has arrived)
                        } else {
                            true // keep optimistic (real not yet arrived)
                        }
                    }
                    // Sort by createdAtMs so optimistic messages slot into the correct
                    // timeline position. Without this, if Realtime delivers message B
                    // before A (possible under flaky network), A_opt would be appended
                    // after B_real even though A was sent first.
                    val sorted = (msgs + remainingOptimistic).sortedBy { it.createdAtMs }
                    s.copy(messages = sorted, isLoading = msgs.isEmpty() && s.isLoading)
                }
            }
            .launchIn(viewModelScope)

        // Show spinner only on first load (Room is empty for this room)
        _state.update { it.copy(isLoading = true) }

        // Background network refresh
        viewModelScope.launch {
            runCatching { messageRepository.refreshMessages(roomId) }
                .onFailure { e ->
                    Log.e(TAG, "refreshMessages failed", e)
                    _state.update { it.copy(error = e.message) }
                }
            _state.update { it.copy(isLoading = false) }
        }

        loadPeerProfile(roomId)

        // Subscribe to realtime (writes to Room → Flow updates UI)
        viewModelScope.launch {
            runCatching {
                messageRepository.subscribeToRoomMessages(roomId) { e ->
                    Log.e(TAG, "realtime error", e)
                }
            }
        }

        viewModelScope.launch {
            runCatching { messageRepository.markAllRead(roomId) }
        }
    }

    private fun loadPeerProfile(roomId: String) {
        viewModelScope.launch {
            val userId = supabase.auth.currentUserOrNull()?.id ?: return@launch
            runCatching {
                withContext(ioDispatcher) {
                    val friendship = supabase.postgrest["friendships"].select {
                        filter {
                            eq("room_id", roomId)
                            eq("status", "accepted")
                        }
                        limit(1L)
                    }.decodeList<FriendshipDto>().firstOrNull()

                    val peerId = friendship?.let {
                        if (it.requesterId == userId) it.addresseeId else it.requesterId
                    }
                    Log.d(TAG, "loadPeerProfile roomId=$roomId peerId=$peerId")

                    if (peerId != null) {
                        supabase.postgrest["profiles"].select {
                            filter { eq("id", peerId) }
                            limit(1L)
                        }.decodeList<ProfileDto>().firstOrNull()?.toDomain()
                    } else null
                }
            }.onSuccess { peer ->
                Log.d(TAG, "loadPeerProfile success: ${peer?.displayName}")
                _state.update { it.copy(peerProfile = peer) }
            }.onFailure {
                Log.e(TAG, "loadPeerProfile failed", it)
            }
        }
    }

    fun onInputChange(text: String) {
        _state.update { it.copy(inputText = text) }
    }

    fun sendMessage() {
        val text = _state.value.inputText.trim()
        if (text.isEmpty() || currentRoomId.isEmpty()) return

        // Optimistic update: show message immediately
        val tempId = "optimistic_${System.currentTimeMillis()}"
        val optimistic = Message(
            id = tempId,
            roomId = currentRoomId,
            senderId = _state.value.currentUserId,
            content = text,
            type = MessageType.TEXT,
            createdAtMs = System.currentTimeMillis(),
            isDeleted = false,
            senderProfile = null,
        )
        _state.update { it.copy(inputText = "", messages = it.messages + optimistic) }

        viewModelScope.launch {
            runCatching { messageRepository.sendMessage(currentRoomId, text) }
                .onSuccess {
                    // Do NOT remove the optimistic message here.
                    // The Room Flow observer (observeMessages.onEach) will automatically
                    // drop it once the real message arrives via Realtime → Room → Flow,
                    // using content-based dedup. Removing it here causes a visible flicker
                    // because the real message hasn't arrived yet at this point.
                    Log.d(TAG, "sendMessage success, waiting for realtime to confirm")
                }
            .onFailure { e ->
                Log.e(TAG, "sendMessage failed", e)
                    _state.update { s ->
                        s.copy(
                            messages = s.messages.filter { it.id != tempId },
                            inputText = text,
                            error = "消息发送失败，请重试",
                        )
                    }
                }
        }
    }

    fun sendImage(
        imageBytes: ByteArray,
        mimeType: String,
        extension: String,
    ) {
        if (currentRoomId.isEmpty()) return
        if (imageBytes.isEmpty()) {
            _state.update { it.copy(error = "图片读取失败") }
            return
        }
        if (imageBytes.size > 5 * 1024 * 1024) {
            _state.update { it.copy(error = "图片不能超过 5MB") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isSendingImage = true) }
            runCatching {
                messageRepository.sendImageMessage(
                    roomId = currentRoomId,
                    imageBytes = imageBytes,
                    contentType = mimeType,
                    extension = extension,
                )
                // 兜底拉取：某些设备/网络上 realtime 可能晚到或偶发丢事件，
                // 图片消息没有 optimistic 文本可先展示，因此这里主动 refresh 一次。
                messageRepository.refreshMessages(currentRoomId)
            }.onFailure { e ->
                Log.e(TAG, "sendImage failed", e)
                _state.update { it.copy(error = "图片发送失败，请重试") }
            }
            _state.update { it.copy(isSendingImage = false) }
        }
    }

    fun sendVoice(audioBytes: ByteArray, durationMs: Long) {
        if (currentRoomId.isEmpty()) return
        if (audioBytes.isEmpty()) {
            _state.update { it.copy(error = "录音失败") }
            return
        }
        if (audioBytes.size > 5 * 1024 * 1024) {
            _state.update { it.copy(error = "语音不能超过 5MB") }
            return
        }

        // 立刻在本地塞一条 optimistic 语音气泡，避免 realtime 晚到/断联时 UI 没反应
        val tempId = "optimistic_voice_${System.currentTimeMillis()}"
        val pending = PendingVoice(bytes = audioBytes, durationMs = durationMs, status = PendingVoice.Status.Sending)
        val optimistic = Message(
            id = tempId,
            roomId = currentRoomId,
            senderId = _state.value.currentUserId,
            content = encodePendingVoiceContent(durationMs, tempId),
            type = MessageType.VOICE,
            createdAtMs = System.currentTimeMillis(),
            isDeleted = false,
            senderProfile = null,
        )
        _state.update {
            it.copy(
                messages = it.messages + optimistic,
                pendingVoices = it.pendingVoices + (tempId to pending),
            )
        }

        uploadAndSendVoice(tempId, audioBytes, durationMs)
    }

    /** 手动重发一条失败的 optimistic 语音。 */
    fun retryVoice(tempId: String) {
        if (currentRoomId.isEmpty()) return
        val pending = _state.value.pendingVoices[tempId] ?: return
        if (pending.status == PendingVoice.Status.Sending) return
        _state.update { s ->
            s.copy(
                pendingVoices = s.pendingVoices + (tempId to pending.copy(status = PendingVoice.Status.Sending)),
            )
        }
        uploadAndSendVoice(tempId, pending.bytes, pending.durationMs)
    }

    private fun uploadAndSendVoice(tempId: String, audioBytes: ByteArray, durationMs: Long) {
        val roomId = currentRoomId
        viewModelScope.launch {
            _state.update { it.copy(isSendingVoice = true) }
            runCatching {
                messageRepository.sendVoiceMessage(
                    roomId = roomId,
                    audioBytes = audioBytes,
                    durationMs = durationMs,
                )
                // 兜底拉取：realtime 断联/延迟时，确保真正的服务端消息落到本地缓存
                runCatching { messageRepository.refreshMessages(roomId) }
            }.onSuccess {
                // 成功：移除本地 optimistic 气泡，由 refresh/realtime 带来的真实消息替代
                _state.update { s ->
                    s.copy(
                        messages = s.messages.filter { it.id != tempId },
                        pendingVoices = s.pendingVoices - tempId,
                    )
                }
            }.onFailure { e ->
                Log.e(TAG, "sendVoice failed (tempId=$tempId)", e)
                _state.update { s ->
                    val current = s.pendingVoices[tempId] ?: return@update s
                    s.copy(
                        pendingVoices = s.pendingVoices + (tempId to current.copy(status = PendingVoice.Status.Failed)),
                        error = "语音发送失败，点击消息可重发",
                    )
                }
            }
            _state.update { it.copy(isSendingVoice = false) }
        }
    }

    /** 放弃一条失败的 optimistic 语音（从本地气泡列表移除）。 */
    fun discardPendingVoice(tempId: String) {
        _state.update { s ->
            s.copy(
                messages = s.messages.filter { it.id != tempId },
                pendingVoices = s.pendingVoices - tempId,
            )
        }
    }

    fun sendSticker(packId: String, stickerId: String) {
        if (currentRoomId.isEmpty()) return
        if (packId.isBlank() || stickerId.isBlank()) return
        val content = StickerRegistry.encodeStickerContent(packId, stickerId)
        val tempId = "optimistic_${System.currentTimeMillis()}"
        val optimistic = Message(
            id = tempId,
            roomId = currentRoomId,
            senderId = _state.value.currentUserId,
            content = content,
            type = MessageType.STICKER,
            createdAtMs = System.currentTimeMillis(),
            isDeleted = false,
            senderProfile = null,
        )
        _state.update { it.copy(messages = it.messages + optimistic) }
        viewModelScope.launch {
            runCatching { messageRepository.sendMessage(currentRoomId, content, type = "sticker") }
                .onFailure { e ->
                    Log.e(TAG, "sendSticker failed", e)
                    _state.update { s ->
                        s.copy(
                            messages = s.messages.filter { it.id != tempId },
                            error = "表情发送失败，请重试",
                        )
                    }
                }
        }
    }

    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            runCatching { messageRepository.softDeleteMessage(messageId) }
                .onSuccess {
                    _state.update { s ->
                        s.copy(messages = s.messages.map { m ->
                            if (m.id == messageId) m.copy(isDeleted = true) else m
                        })
                    }
                }
        }
    }

    override fun onCleared() {
        viewModelScope.launch {
            runCatching { messageRepository.unsubscribeFromRoom(currentRoomId) }
        }
        super.onCleared()
    }
}
