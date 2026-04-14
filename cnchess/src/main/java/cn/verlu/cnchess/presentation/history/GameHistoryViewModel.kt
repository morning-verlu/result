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
    val items: List<GameHistoryItem> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class GameHistoryViewModel @Inject constructor(
    private val gameRepository: GameRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(GameHistoryState())
    val state: StateFlow<GameHistoryState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            runCatching { gameRepository.listRecentGames() }
                .onSuccess { list ->
                    _state.update { it.copy(isLoading = false, items = list) }
                }
                .onFailure { e ->
                    _state.update { it.copy(isLoading = false, error = e.message ?: "加载历史失败") }
                }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}
