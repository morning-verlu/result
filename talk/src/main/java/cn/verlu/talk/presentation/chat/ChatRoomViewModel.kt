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
    val error: String? = null,
)

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
                    // On failure, roll back: remove the optimistic bubble and restore input text.
                    _state.update { s ->
                        s.copy(
                            messages = s.messages.filter { it.id != tempId },
                            inputText = text,
                            error = "发送失败：${e.message}",
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
