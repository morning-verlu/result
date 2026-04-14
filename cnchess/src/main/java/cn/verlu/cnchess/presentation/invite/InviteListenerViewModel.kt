package cn.verlu.cnchess.presentation.invite

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.verlu.cnchess.data.repository.InviteRepository
import cn.verlu.cnchess.data.repository.PresenceRepository
import cn.verlu.cnchess.domain.model.ChessInvite
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class InviteListenerState(
    val incomingInvite: ChessInvite? = null,
    val error: String? = null,
)

@HiltViewModel
class InviteListenerViewModel @Inject constructor(
    private val inviteRepository: InviteRepository,
    private val presenceRepository: PresenceRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(InviteListenerState())
    val state: StateFlow<InviteListenerState> = _state.asStateFlow()

    private val navigateToGameChannel = Channel<String>(Channel.BUFFERED)
    val navigateToGame = navigateToGameChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            runCatching {
                inviteRepository.refreshInvites()
                inviteRepository.subscribeInviteChanges()
            }.onFailure { e ->
                _state.update { it.copy(error = e.message ?: "初始化邀请监听失败") }
            }
        }
        viewModelScope.launch {
            inviteRepository.incomingPendingInvites.collect { list ->
                _state.update { it.copy(incomingInvite = list.firstOrNull()) }
            }
        }
        viewModelScope.launch {
            inviteRepository.gameLaunchEvents.collect { gameId ->
                navigateToGameChannel.send(gameId)
            }
        }
    }

    fun acceptInvite(inviteId: String) {
        viewModelScope.launch {
            runCatching {
                val gameId = inviteRepository.acceptInvite(inviteId)
                navigateToGameChannel.send(gameId)
            }.onFailure { e ->
                _state.update { it.copy(error = e.message ?: "接受邀请失败") }
            }
        }
    }

    fun rejectInvite(inviteId: String) {
        viewModelScope.launch {
            runCatching { inviteRepository.rejectInvite(inviteId) }
                .onFailure { e -> _state.update { it.copy(error = e.message ?: "拒绝邀请失败") } }
        }
    }

    fun setForeground(isForeground: Boolean) {
        viewModelScope.launch {
            runCatching { presenceRepository.setForeground(isForeground) }
        }
    }

    fun heartbeat() {
        viewModelScope.launch {
            runCatching { presenceRepository.heartbeat() }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    override fun onCleared() {
        viewModelScope.launch {
            runCatching { inviteRepository.unsubscribeInviteChanges() }
        }
        super.onCleared()
    }
}
