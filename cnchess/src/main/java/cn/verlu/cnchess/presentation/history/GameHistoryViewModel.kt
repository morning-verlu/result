package cn.verlu.cnchess.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.verlu.cnchess.data.repository.GameRepository
import cn.verlu.cnchess.domain.model.GameHistoryItem
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GameHistoryState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val items: List<GameHistoryItem> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class GameHistoryViewModel @Inject constructor(
    private val gameRepository: GameRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(GameHistoryState())
    val state: StateFlow<GameHistoryState> = _state.asStateFlow()

    private var initialLoadDone = false

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { s ->
                val showBlocking = !initialLoadDone && s.items.isEmpty()
                if (showBlocking) {
                    s.copy(isLoading = true, isRefreshing = false, error = null)
                } else {
                    s.copy(isRefreshing = true, error = null)
                }
            }
            runCatching { gameRepository.listRecentGames() }
                .onSuccess { list ->
                    initialLoadDone = true
                    _state.update { it.copy(isLoading = false, isRefreshing = false, items = list) }
                }
                .onFailure { e ->
                    initialLoadDone = true
                    _state.update {
                        it.copy(isLoading = false, isRefreshing = false, error = e.message ?: "加载历史失败")
                    }
                }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}
