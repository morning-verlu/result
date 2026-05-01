package cn.verlu.lulu.core.mvi

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update

abstract class MviViewModel<UiState, UiIntent, UiEffect>(
    initialState: UiState,
) : ViewModel() {
    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _effects = Channel<UiEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    abstract fun onIntent(intent: UiIntent)

    protected fun reduce(block: (UiState) -> UiState) {
        _state.update(block)
    }

    protected fun currentState(): UiState = _state.value

    protected suspend fun emitEffect(effect: UiEffect) {
        _effects.send(effect)
    }
}
