package cn.verlu.lulu.presentation.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.verlu.lulu.domain.chat.ChatRepository
import cn.verlu.lulu.presentation.chat.ChatContract.Effect
import cn.verlu.lulu.presentation.chat.ChatContract.Intent
import cn.verlu.lulu.presentation.chat.ChatContract.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<Effect>()
    val effects: SharedFlow<Effect> = _effects.asSharedFlow()

    private var messagesJob: Job? = null

    init {
        observeConversations()
    }

    fun onIntent(intent: Intent) {
        when (intent) {
            Intent.NewConversation -> newConversation()
            is Intent.SelectConversation -> selectConversation(intent.conversationId)
            is Intent.InputChanged -> _state.update { it.copy(input = intent.value) }
            Intent.SendMessage -> sendMessage()
            is Intent.SaveAssistantMessage -> saveAssistantMessage(intent.messageId)
            Intent.ClearError -> _state.update { it.copy(errorMessage = null) }
        }
    }

    private fun observeConversations() {
        viewModelScope.launch {
            chatRepository.observeConversations().collectLatest { conversations ->
                val currentId = _state.value.selectedConversationId
                val nextId = currentId?.takeIf { id -> conversations.any { it.id == id } }
                    ?: conversations.firstOrNull()?.id
                _state.update {
                    it.copy(
                        conversations = conversations,
                        selectedConversationId = nextId,
                        messages = if (nextId != currentId) emptyList() else it.messages,
                        isLoading = false,
                    )
                }
                if (nextId != null && nextId != currentId) {
                    observeMessages(nextId)
                }
            }
        }
    }

    private fun newConversation() {
        viewModelScope.launch {
            runCatching { chatRepository.startConversation() }
                .onSuccess { selectConversation(it) }
                .onFailure { throwable ->
                    _state.update { it.copy(errorMessage = throwable.message ?: "新建会话失败") }
                }
        }
    }

    private fun selectConversation(conversationId: String) {
        _state.update { it.copy(selectedConversationId = conversationId, messages = emptyList()) }
        observeMessages(conversationId)
    }

    private fun observeMessages(conversationId: String) {
        messagesJob?.cancel()
        messagesJob = viewModelScope.launch {
            chatRepository.observeMessages(conversationId).collectLatest { messages ->
                _state.update { it.copy(messages = messages) }
            }
        }
    }

    private fun sendMessage() {
        val state = _state.value
        val text = state.input.trim()
        if (text.isBlank() || state.isSending) return

        viewModelScope.launch {
            _state.update { it.copy(isSending = true, errorMessage = null) }
            runCatching {
                chatRepository.sendMessage(
                    conversationId = state.selectedConversationId,
                    content = text,
                )
            }.onSuccess { conversationId ->
                _state.update {
                    it.copy(
                        selectedConversationId = conversationId,
                        input = "",
                        isSending = false,
                    )
                }
                observeMessages(conversationId)
            }.onFailure { throwable ->
                _state.update {
                    it.copy(
                        isSending = false,
                        errorMessage = throwable.message ?: "发送失败",
                    )
                }
            }
        }
    }

    private fun saveAssistantMessage(messageId: String) {
        viewModelScope.launch {
            runCatching { chatRepository.saveAssistantMessageAsMemory(messageId) }
                .onSuccess { _effects.emit(Effect.ShowMessage("已整理成记忆")) }
                .onFailure { throwable ->
                    _effects.emit(Effect.ShowMessage(throwable.message ?: "保存失败"))
                }
        }
    }
}
