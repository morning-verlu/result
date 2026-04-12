package cn.verlu.sync.presentation.screentime.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.verlu.sync.domain.usecase.LoadScreenTimeUseCase
import cn.verlu.sync.domain.usecase.ObserveSyncedScreenTimeReportsUseCase
import cn.verlu.sync.domain.usecase.RefreshSyncedScreenTimeReportsUseCase
import cn.verlu.sync.domain.usecase.UploadScreenTimeReportUseCase
import cn.verlu.sync.presentation.screentime.mvi.ScreenTimeContract
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ScreenTimeViewModel @Inject constructor(
    private val loadScreenTimeUseCase: LoadScreenTimeUseCase,
    private val uploadScreenTimeReportUseCase: UploadScreenTimeReportUseCase,
    private val observeSyncedScreenTimeReportsUseCase: ObserveSyncedScreenTimeReportsUseCase,
    private val refreshSyncedScreenTimeReportsUseCase: RefreshSyncedScreenTimeReportsUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(ScreenTimeContract.State())
    val state: StateFlow<ScreenTimeContract.State> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state
                .map { it.period }
                .distinctUntilChanged()
                .flatMapLatest { observeSyncedScreenTimeReportsUseCase(it) }
                .collect { list ->
                    _state.update { it.copy(syncedReports = list) }
                }
        }
    }

    fun dispatch(intent: ScreenTimeContract.Intent) {
        when (intent) {
            ScreenTimeContract.Intent.Load -> load(isSilent = true)
            is ScreenTimeContract.Intent.Refresh -> load(isSilent = intent.isSilent)
            is ScreenTimeContract.Intent.SelectPeriod -> {
                _state.update {
                    it.copy(period = intent.period, expandedRowKey = null)
                }
                load(isSilent = true)
            }
            is ScreenTimeContract.Intent.ToggleExpand -> {
                _state.update {
                    val key = intent.rowKey
                    it.copy(
                        expandedRowKey = if (it.expandedRowKey == key) null else key
                    )
                }
            }
            is ScreenTimeContract.Intent.SetMyUserId -> _state.update { it.copy(myUserId = intent.userId) }
        }
    }

    private fun load(isSilent: Boolean) {
        viewModelScope.launch {
            val access = loadScreenTimeUseCase.hasUsageAccess()
            _state.update { it.copy(hasUsageAccess = access) }

            _state.update { it.copy(isRefreshing = !isSilent, listError = null) }
            
            // 先将本机的使用时长同步到服务器
            if (access) {
                runCatching { 
                    val summary = loadScreenTimeUseCase(_state.value.period)
                    uploadScreenTimeReportUseCase(_state.value.period, summary) 
                }
            }

            // 然后再拉取包含他人的最新列表
            runCatching {
                refreshSyncedScreenTimeReportsUseCase(_state.value.period)
            }.onFailure { e ->
                _state.update {
                    it.copy(listError = e.message ?: "同步列表失败")
                }
            }
            _state.update { it.copy(isRefreshing = false) }
        }
    }
}
