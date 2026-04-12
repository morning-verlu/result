package cn.verlu.talk.presentation.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.verlu.talk.data.repository.FriendRepository
import cn.verlu.talk.domain.model.Friendship
import cn.verlu.talk.domain.model.Profile
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ContactsState(
    val friends: List<Friendship> = emptyList(),
    val pendingRequests: List<Friendship> = emptyList(),
    val selectedTabIndex: Int = 0,
    /** 首次进入：等会话就绪并拉完好友后再关，避免误显示「还没有好友」 */
    val isInitialLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val showAddFriendDialog: Boolean = false,
    val searchQuery: String = "",
    val searchResult: Profile? = null,
    val isSearching: Boolean = false,
    val searchError: String? = null,
    val addFriendSuccess: Boolean = false,
    val currentUserId: String = "",
)

@HiltViewModel
class ContactsViewModel @Inject constructor(
    private val friendRepository: FriendRepository,
    private val supabase: SupabaseClient,
) : ViewModel() {

    private val _state = MutableStateFlow(ContactsState())
    val state: StateFlow<ContactsState> = _state.asStateFlow()

    private val navigateToChatChannel = Channel<String>(Channel.BUFFERED)
    val navigateToChat = navigateToChatChannel.receiveAsFlow()

    init {
        // Observe Room cache — instant display once DB 有数据
        friendRepository.observeAcceptedFriends()
            .onEach { list ->
                _state.update { s ->
                    s.copy(friends = list.sortedBy { f -> f.peerProfile(s.currentUserId)?.displayName ?: "" })
                }
            }
            .launchIn(viewModelScope)

        // 会话恢复后再订阅「待处理申请」并拉网，避免 userId 为空导致一直像空列表
        viewModelScope.launch {
            supabase.auth.sessionStatus.first { it !is SessionStatus.Initializing }
            val userId = supabase.auth.currentUserOrNull()?.id.orEmpty()
            _state.update { it.copy(currentUserId = userId) }

            friendRepository.observePendingRequests(userId)
                .onEach { list -> _state.update { it.copy(pendingRequests = list) } }
                .launchIn(viewModelScope)

            runCatching { friendRepository.refreshFriends() }
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
            _state.update { it.copy(isInitialLoading = false) }
        }

        // Subscribe to realtime (writes to Room → Flow updates UI)
        viewModelScope.launch {
            runCatching { friendRepository.subscribeToFriendshipChanges() }
        }
    }

    fun selectTab(index: Int) = _state.update { it.copy(selectedTabIndex = index) }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isRefreshing = true) }
            runCatching { friendRepository.refreshFriends() }
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
            _state.update { it.copy(isRefreshing = false) }
        }
    }

    fun openChatWithPeer(peerUserId: String) {
        val friendship = _state.value.friends.find {
            it.requesterId == peerUserId || it.addresseeId == peerUserId
        }
        val roomId = friendship?.roomId
        if (roomId != null) {
            viewModelScope.launch { navigateToChatChannel.send(roomId) }
        } else {
            _state.update { it.copy(error = "未找到聊天室，请等待好友关系同步后重试") }
        }
    }

    fun showAddFriendDialog() = _state.update {
        it.copy(
            showAddFriendDialog = true,
            searchQuery = "",
            searchResult = null,
            searchError = null,
            addFriendSuccess = false,
        )
    }

    fun hideAddFriendDialog() = _state.update { it.copy(showAddFriendDialog = false) }

    fun setSearchQuery(q: String) = _state.update {
        it.copy(searchQuery = q, searchResult = null, searchError = null)
    }

    fun searchUser() {
        val query = _state.value.searchQuery.trim()
        if (query.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(isSearching = true, searchError = null, searchResult = null) }
            runCatching { friendRepository.searchUser(query) }
                .onSuccess { profile ->
                    when {
                        profile == null ->
                            _state.update { it.copy(isSearching = false, searchError = "未找到该用户（请确认邮箱正确）") }
                        profile.id == _state.value.currentUserId ->
                            _state.update { it.copy(isSearching = false, searchError = "不能添加自己为好友") }
                        _state.value.friends.any { f ->
                            f.requesterId == profile.id || f.addresseeId == profile.id
                        } ->
                            _state.update { it.copy(isSearching = false, searchError = "已经是好友了") }
                        else ->
                            _state.update { it.copy(isSearching = false, searchResult = profile) }
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(isSearching = false, searchError = e.message) }
                }
        }
    }

    fun sendFriendRequest(addresseeId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isSearching = true) }
            runCatching { friendRepository.sendFriendRequest(addresseeId) }
                .onSuccess {
                    _state.update { it.copy(isSearching = false, addFriendSuccess = true) }
                }
                .onFailure { e ->
                    val msg = when {
                        e.message?.contains("reverse_request_exists", ignoreCase = true) == true ->
                            "对方已向你发送了好友申请，请在「新的朋友」中查看并同意"
                        e.message?.contains("unique", ignoreCase = true) == true ||
                        e.message?.contains("duplicate", ignoreCase = true) == true ->
                            "已向该用户发送过申请，等待对方同意"
                        else -> "发送失败: ${e.message}"
                    }
                    _state.update { it.copy(isSearching = false, searchError = msg) }
                }
        }
    }

    fun acceptRequest(friendshipId: String) {
        viewModelScope.launch {
            runCatching { friendRepository.acceptFriendRequest(friendshipId) }
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
            // Room cache will update via realtime after accept
        }
    }

    fun rejectRequest(friendshipId: String) {
        viewModelScope.launch {
            runCatching { friendRepository.rejectFriendRequest(friendshipId) }
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    override fun onCleared() {
        viewModelScope.launch {
            runCatching { friendRepository.unsubscribeFromFriendshipChanges() }
        }
        super.onCleared()
    }
}
