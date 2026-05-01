package cn.verlu.lulu.presentation.mine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.verlu.lulu.domain.memory.MemoryRepository
import cn.verlu.lulu.domain.memory.MemorySyncSummary
import cn.verlu.lulu.feature.lifestream.data.local.MemorySettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MineUiState(
    val syncSummary: MemorySyncSummary = MemorySyncSummary(),
    val cloudSyncEnabled: Boolean = false,
    val showCloudBadge: Boolean = true,
    val mediaCdnBaseUrl: String = "",
    val isExporting: Boolean = false,
    val isImporting: Boolean = false,
    val message: String? = null,
)

sealed interface MineEffect {
    data class ShareMemoryJson(val json: String) : MineEffect
}

@HiltViewModel
class MineViewModel @Inject constructor(
    private val memoryRepository: MemoryRepository,
    private val memorySettingsStore: MemorySettingsStore,
) : ViewModel() {
    private val _state = MutableStateFlow(MineUiState())
    val state: StateFlow<MineUiState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<MineEffect>()
    val effects: SharedFlow<MineEffect> = _effects.asSharedFlow()

    init {
        viewModelScope.launch {
            memoryRepository.observeSyncSummary().collectLatest { summary ->
                _state.update { it.copy(syncSummary = summary) }
            }
        }
        viewModelScope.launch {
            memorySettingsStore.cloudSyncEnabled.collectLatest { enabled ->
                _state.update { it.copy(cloudSyncEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            memorySettingsStore.showCloudBadge.collectLatest { enabled ->
                _state.update { it.copy(showCloudBadge = enabled) }
            }
        }
        viewModelScope.launch {
            memorySettingsStore.mediaCdnBaseUrl.collectLatest { baseUrl ->
                _state.update { it.copy(mediaCdnBaseUrl = baseUrl) }
            }
        }
    }

    fun retrySync() {
        viewModelScope.launch {
            runCatching { memoryRepository.retryFailed() }
                .onFailure { throwable ->
                    _state.update { it.copy(message = throwable.message ?: "同步失败") }
                }
        }
    }

    fun exportMemories() {
        viewModelScope.launch {
            _state.update { it.copy(isExporting = true, message = null) }
            runCatching { memoryRepository.exportEntriesJson() }
                .onSuccess { json ->
                    _effects.emit(MineEffect.ShareMemoryJson(json))
                    _state.update { it.copy(isExporting = false) }
                }
                .onFailure { throwable ->
                    _state.update {
                        it.copy(
                            isExporting = false,
                            message = throwable.message ?: "导出失败",
                        )
                    }
                }
        }
    }

    fun importMemories(json: String) {
        viewModelScope.launch {
            _state.update { it.copy(isImporting = true, message = null) }
            runCatching { memoryRepository.importEntriesJson(json) }
                .onSuccess { summary ->
                    _state.update {
                        it.copy(
                            syncSummary = summary,
                            isImporting = false,
                            message = "记忆已导入",
                        )
                    }
                }
                .onFailure { throwable ->
                    _state.update {
                        it.copy(
                            isImporting = false,
                            message = throwable.message ?: "导入失败",
                        )
                    }
                }
        }
    }

    fun setCloudSyncEnabled(enabled: Boolean) {
        memorySettingsStore.setCloudSyncEnabled(enabled)
        if (enabled) {
            retrySync()
        }
    }

    fun setShowCloudBadge(enabled: Boolean) {
        memorySettingsStore.setShowCloudBadge(enabled)
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }
}
