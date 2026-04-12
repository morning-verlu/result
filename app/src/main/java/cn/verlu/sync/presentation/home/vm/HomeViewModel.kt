package cn.verlu.sync.presentation.home.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.verlu.sync.domain.usecase.ObserveBatteryListUseCase
import cn.verlu.sync.domain.usecase.RefreshBatteryListUseCase
import cn.verlu.sync.domain.usecase.StartSyncUseCase
import cn.verlu.sync.presentation.home.mvi.HomeContract
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val observeBatteryListUseCase: ObserveBatteryListUseCase,
    private val startSyncUseCase: StartSyncUseCase,
    private val refreshBatteryListUseCase: RefreshBatteryListUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(HomeContract.State())
    val state: StateFlow<HomeContract.State> = _state.asStateFlow()

    fun dispatch(intent: HomeContract.Intent) {
        when (intent) {
            HomeContract.Intent.Start -> start()
            is HomeContract.Intent.Refresh -> refresh(intent.isSilent)
            is HomeContract.Intent.SetMyUserId -> _state.update { it.copy(myUserId = intent.userId) }
        }
    }

    private fun start() {
        viewModelScope.launch {
            runCatching { startSyncUseCase() }
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
            observeBatteryListUseCase().collectLatest { items ->
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
                // 先同步本机的电量到远端
                runCatching { startSyncUseCase() }
                
                // 然后刷新远端列表
                runCatching { refreshBatteryListUseCase() }
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
