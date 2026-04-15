package cn.verlu.talk.presentation.conversations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.verlu.talk.data.repository.MessageRepository
import cn.verlu.talk.domain.model.Conversation
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ConversationListState(
    val conversations: List<Conversation> = emptyList(),
    /** 首次进入：Room 可能尚为空，等第一次网络刷新结束后再关掉，避免误显示「空态」 */
    val isInitialLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val searchQuery: String = "",
    val error: String? = null,
) {
    val filtered: List<Conversation>
        get() = if (searchQuery.isBlank()) conversations
        else conversations.filter {
            it.peer.displayName.contains(searchQuery, ignoreCase = true)
        }
}

@HiltViewModel
class ConversationListViewModel @Inject constructor(
    private val messageRepository: MessageRepository,
    private val supabase: SupabaseClient,
) : ViewModel() {

    private val _state = MutableStateFlow(ConversationListState())
    val state: StateFlow<ConversationListState> = _state.asStateFlow()

    init {
        // Observe Room cache — instant, no spinner
        messageRepository.observeConversations()
            .onEach { list ->
                _state.update { prev ->
                    prev.copy(
                        conversations = list,
                        // 只要本地已有会话，进入页面就不应再显示「初始加载」动画。
                        isInitialLoading = if (list.isNotEmpty()) false else prev.isInitialLoading,
                    )
                }
            }
            .launchIn(viewModelScope)

        // 会话从存储恢复后再拉列表，否则易未登录/空表；首刷结束后再取消「初始加载中」
        viewModelScope.launch {
            supabase.auth.sessionStatus.first { it !is SessionStatus.Initializing }
            runCatching { messageRepository.refreshConversations() }
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
            _state.update { it.copy(isInitialLoading = false) }
        }

        // Subscribe to realtime (writes to Room, which triggers the Flow above)
        viewModelScope.launch {
            runCatching {
                messageRepository.subscribeToConversationUpdates { /* Room flow handles UI */ }
            }
        }
    }

    /** Pull-to-refresh: shows spinner, then does a fresh network fetch. */
    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isRefreshing = true, error = null) }
            runCatching { messageRepository.refreshConversations() }
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
            _state.update { it.copy(isRefreshing = false) }
        }
    }

    fun refreshSilently() {
        viewModelScope.launch {
            runCatching { messageRepository.refreshConversations() }
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    fun setSearchQuery(q: String) {
        _state.update { it.copy(searchQuery = q) }
    }

    override fun onCleared() {
        viewModelScope.launch {
            runCatching { messageRepository.unsubscribeFromConversationUpdates() }
        }
        super.onCleared()
    }
}
