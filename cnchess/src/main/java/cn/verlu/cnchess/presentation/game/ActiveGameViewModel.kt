package cn.verlu.cnchess.presentation.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.verlu.cnchess.data.repository.GameRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Checked once after authentication to detect an in-progress game and
 * redirect the user back into it automatically.
 *
 * State values:
 *   null  – check not yet started or in progress (show loading overlay)
 *   ""    – checked, no active game
 *   else  – checked, active game id
 */
@HiltViewModel
class ActiveGameViewModel @Inject constructor(
    private val gameRepository: GameRepository,
) : ViewModel() {

    private val _activeGameId = MutableStateFlow<String?>(null)
    val activeGameId: StateFlow<String?> = _activeGameId.asStateFlow()

    fun checkOnce() {
        if (_activeGameId.value != null) return
        viewModelScope.launch {
            val id = runCatching { gameRepository.findActiveGame() }.getOrNull() ?: ""
            _activeGameId.value = id
        }
    }

    fun reset() {
        _activeGameId.value = null
    }
}
