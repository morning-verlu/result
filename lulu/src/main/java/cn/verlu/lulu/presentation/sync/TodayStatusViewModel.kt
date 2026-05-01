package cn.verlu.lulu.presentation.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.verlu.lulu.domain.memory.Memory
import cn.verlu.lulu.domain.memory.MemoryRepository
import cn.verlu.lulu.domain.memory.MemorySyncSummary
import cn.verlu.lulu.domain.sync.SyncStatusRepository
import cn.verlu.lulu.domain.sync.SyncStatusType
import cn.verlu.lulu.domain.sync.TodayStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class TodayStatusViewModel @Inject constructor(
    private val syncStatusRepository: SyncStatusRepository,
    private val memoryRepository: MemoryRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(TodayStatusUiState())
    val state: StateFlow<TodayStatusUiState> = _state.asStateFlow()

    init {
        refresh()
        observeRecentMemories()
        observeMemorySyncSummary()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching { syncStatusRepository.loadTodayStatus() }
                .onSuccess { todayStatus ->
                    _state.update {
                        it.mergeStatus(todayStatus.toStatusUiState()).copy(isLoading = false)
                    }
                }
                .onFailure { throwable ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = throwable.message ?: "今日状态加载失败",
                        )
                    }
                }
        }
    }

    private fun observeRecentMemories() {
        viewModelScope.launch {
            memoryRepository.observeEntries().collectLatest { memories ->
                _state.update {
                    it.copy(recentMemories = memories.take(3))
                }
            }
        }
    }

    private fun observeMemorySyncSummary() {
        viewModelScope.launch {
            memoryRepository.observeSyncSummary().collectLatest { summary ->
                _state.update { it.copy(memorySyncSummary = summary) }
            }
        }
    }

    private fun TodayStatus.toStatusUiState(): TodayStatusUiState =
        TodayStatusUiState(
            weather = SyncStatusCardUiState(
                type = SyncStatusType.Weather,
                title = "天气",
                value = weather.temperatureCelsius?.let { "$it°C" } ?: "--",
                detail = weather.condition ?: "待接入",
            ),
            battery = SyncStatusCardUiState(
                type = SyncStatusType.Battery,
                title = "电量",
                value = battery.percent?.let { "$it%" } ?: "--",
                detail = if (battery.isCharging) "正在充电" else "本机电量",
            ),
            screenTime = SyncStatusCardUiState(
                type = SyncStatusType.ScreenTime,
                title = "屏幕时间",
                value = screenTime.totalMinutes?.toDurationText() ?: "--",
                detail = screenTime.detail,
            ),
            deviceTemperature = SyncStatusCardUiState(
                type = SyncStatusType.DeviceTemperature,
                title = "设备温度",
                value = deviceTemperature.celsius?.let { "${it.toInt()}°C" } ?: "--",
                detail = deviceTemperature.label ?: "待接入",
            ),
        )

    private fun TodayStatusUiState.mergeStatus(status: TodayStatusUiState): TodayStatusUiState =
        copy(
            weather = status.weather,
            battery = status.battery,
            screenTime = status.screenTime,
            deviceTemperature = status.deviceTemperature,
        )

    private fun Int.toDurationText(): String {
        val hours = this / 60
        val minutes = this % 60
        return when {
            hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
            hours > 0 -> "${hours}h"
            else -> "${minutes}m"
        }
    }
}

data class TodayStatusUiState(
    val weather: SyncStatusCardUiState = SyncStatusCardUiState(
        type = SyncStatusType.Weather,
        title = "天气",
        value = "--",
        detail = "待加载",
    ),
    val battery: SyncStatusCardUiState = SyncStatusCardUiState(
        type = SyncStatusType.Battery,
        title = "电量",
        value = "--",
        detail = "待加载",
    ),
    val screenTime: SyncStatusCardUiState = SyncStatusCardUiState(
        type = SyncStatusType.ScreenTime,
        title = "屏幕时间",
        value = "--",
        detail = "待加载",
    ),
    val deviceTemperature: SyncStatusCardUiState = SyncStatusCardUiState(
        type = SyncStatusType.DeviceTemperature,
        title = "设备温度",
        value = "--",
        detail = "待加载",
    ),
    val recentMemories: List<Memory> = emptyList(),
    val memorySyncSummary: MemorySyncSummary = MemorySyncSummary(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
) {
    val cards: List<SyncStatusCardUiState>
        get() = listOf(weather, battery, screenTime, deviceTemperature)
}

data class SyncStatusCardUiState(
    val type: SyncStatusType,
    val title: String,
    val value: String,
    val detail: String,
)
