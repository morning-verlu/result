package cn.verlu.sync.presentation.temperature.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.verlu.sync.domain.usecase.ObserveTemperatureListUseCase
import cn.verlu.sync.domain.usecase.RefreshTemperatureListUseCase
import cn.verlu.sync.presentation.temperature.mvi.TemperatureContract
import cn.verlu.sync.domain.repository.TemperatureRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class TemperatureViewModel @Inject constructor(
    private val observeTemperatureListUseCase: ObserveTemperatureListUseCase,
    private val refreshTemperatureListUseCase: RefreshTemperatureListUseCase,
    private val temperatureRepository: TemperatureRepository
) : ViewModel() {

    private val _state = MutableStateFlow(TemperatureContract.State())
    val state: StateFlow<TemperatureContract.State> = _state.asStateFlow()

    fun dispatch(intent: TemperatureContract.Intent) {
        when (intent) {
            TemperatureContract.Intent.Start -> start()
            is TemperatureContract.Intent.Refresh -> refresh(intent.isSilent)
            is TemperatureContract.Intent.SetMyUserId -> _state.update { it.copy(myUserId = intent.userId) }
        }
    }

    private fun start() {
        viewModelScope.launch {
            runCatching { temperatureRepository.startSync() }
                .onSuccess {
                    _state.update { it.copy(error = null) }
                    // 启动成功后，静默刷新一次数据
                    refresh(isSilent = true)
                }
                .onFailure { throwable ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = throwable.message ?: "同步启动失败"
                        )
                    }
                }
        }

        viewModelScope.launch {
            observeTemperatureListUseCase().collectLatest { items ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        items = items
                    )
                }
            }
        }
    }

    private fun refresh(isSilent: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(isRefreshing = !isSilent) }
            try {
                runCatching { refreshTemperatureListUseCase() }
                    .onSuccess { _state.update { it.copy(error = null) } }
                    .onFailure { e ->
                        _state.update {
                            it.copy(error = e.message ?: "刷新失败")
                        }
                    }
            } finally {
                _state.update { it.copy(isRefreshing = false) }
            }
        }
    }
}
