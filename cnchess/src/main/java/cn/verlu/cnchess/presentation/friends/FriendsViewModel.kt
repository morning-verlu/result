package cn.verlu.cnchess.presentation.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.verlu.cnchess.data.repository.FriendRepository
import cn.verlu.cnchess.data.repository.GameRepository
import cn.verlu.cnchess.data.repository.InviteRepository
import cn.verlu.cnchess.data.repository.PresenceRepository
import cn.verlu.cnchess.domain.model.ChessInvite
import cn.verlu.cnchess.domain.model.Friendship
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FriendsUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val friends: List<Friendship> = emptyList(),
    val onlinePeerUserIds: Set<String> = emptySet(),
    val inGamePeerUserIds: Set<String> = emptySet(),
    val outgoingInviteToUserIds: Set<String> = emptySet(),
    /** 当前待对方接受的邀请（用于弹窗）；有多条时取最近一条 */
    val pendingOutgoingInvite: ChessInvite? = null,
    val isMyselfInGame: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class FriendsViewModel @Inject constructor(
    private val friendRepository: FriendRepository,
    private val inviteRepository: InviteRepository,
    private val presenceRepository: PresenceRepository,
    private val gameRepository: GameRepository,
    private val supabase: SupabaseClient,
) : ViewModel() {

    private val _state = MutableStateFlow(FriendsUiState())
    val state: StateFlow<FriendsUiState> = _state.asStateFlow()

    init {
        friendRepository.friends
            .combine(inviteRepository.outgoingPendingInvites) { friends, outgoing ->
                Triple(
                    friends,
                    outgoing.map { it.toUserId }.toSet(),
                    outgoing.firstOrNull(),
                )
            }.let { flow ->
                viewModelScope.launch {
                    flow.collect { (friends, outgoingUserIds, firstOutgoing) ->
                        _state.update {
                            it.copy(
                                isLoading = false,
                                friends = friends,
                                outgoingInviteToUserIds = outgoingUserIds,
                                pendingOutgoingInvite = firstOutgoing,
                            )
                        }
                        refreshOnlineStatuses(friends)
                    }
                }
            }

        viewModelScope.launch {
            runCatching {
                friendRepository.refreshFriends()
                inviteRepository.refreshInvites()
                friendRepository.subscribeToFriendshipChanges()
            }.onFailure { e ->
                _state.update { it.copy(isLoading = false, error = e.message ?: "加载好友失败") }
            }
        }
    }

    fun invite(peerUserId: String) {
        viewModelScope.launch {
            runCatching {
                inviteRepository.sendInvite(peerUserId)
            }.onFailure { e ->
                _state.update { it.copy(error = e.message ?: "发送邀请失败") }
            }
        }
    }

    fun cancelInvite(peerUserId: String) {
        val outgoing = inviteRepository.outgoingPendingInvites.value.firstOrNull { it.toUserId == peerUserId } ?: return
        viewModelScope.launch {
            runCatching {
                inviteRepository.cancelInvite(outgoing.id)
            }.onFailure { e ->
                _state.update { it.copy(error = e.message ?: "取消邀请失败") }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isRefreshing = true) }
            runCatching {
                friendRepository.refreshFriends()
                inviteRepository.refreshInvites()
                refreshOnlineStatuses(_state.value.friends)
            }.onFailure { e ->
                _state.update { it.copy(error = e.message ?: "刷新失败") }
            }.also {
                _state.update { it.copy(isRefreshing = false, isLoading = false) }
            }
        }
    }

    fun refreshSilently() {
        viewModelScope.launch {
            runCatching {
                friendRepository.refreshFriends()
                inviteRepository.refreshInvites()
                refreshOnlineStatuses(_state.value.friends)
            }.onFailure { e ->
                _state.update { it.copy(error = e.message ?: "刷新失败") }
            }
        }
    }

    fun refreshOnlineStatusesNow() {
        refreshOnlineStatuses(_state.value.friends)
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    private fun refreshOnlineStatuses(friends: List<Friendship>) {
        val currentUserId = supabase.auth.currentUserOrNull()?.id ?: return
        viewModelScope.launch {
            val peerIds = friends.mapNotNull { it.peerProfile(currentUserId)?.id }
            val onlineIds = buildSet {
                peerIds.forEach { peerId ->
                    val online = runCatching { presenceRepository.isUserOnline(peerId) }.getOrDefault(false)
                    if (online) add(peerId)
                }
            }
            val activeOpponents = runCatching { gameRepository.getActiveOpponentIds() }.getOrDefault(emptySet())
            val inGamePeerUserIds = peerIds.filter { activeOpponents.contains(it) }.toSet()
            val isMyselfInGame = activeOpponents.isNotEmpty()
            _state.update {
                it.copy(
                    onlinePeerUserIds = onlineIds,
                    inGamePeerUserIds = inGamePeerUserIds,
                    isMyselfInGame = isMyselfInGame,
                )
            }
        }
    }

    override fun onCleared() {
        viewModelScope.launch {
            runCatching { friendRepository.unsubscribeFromFriendshipChanges() }
        }
        super.onCleared()
    }
}
