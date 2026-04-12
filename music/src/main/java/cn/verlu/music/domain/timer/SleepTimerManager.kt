package cn.verlu.music.domain.timer

import cn.verlu.music.domain.player.AudioPlayerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** 到达设定时间后自动暂停播放（定时关闭）。 */
@Singleton
class SleepTimerManager @Inject constructor(
    private val audioPlayerManager: AudioPlayerManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var timerJob: Job? = null

    private val _endAtEpochMs = MutableStateFlow<Long?>(null)
    val endAtEpochMs: StateFlow<Long?> = _endAtEpochMs.asStateFlow()

    /** 当前选中的定时档位（分钟），仅用于 UI 高亮；无定时时为 null */
    private val _scheduledMinutes = MutableStateFlow<Int?>(null)
    val scheduledMinutes: StateFlow<Int?> = _scheduledMinutes.asStateFlow()

    fun scheduleStopAfter(durationMs: Long) {
        cancel()
        if (durationMs <= 0L) return
        val minutes = (durationMs / 60_000L).toInt().coerceAtLeast(1)
        _scheduledMinutes.value = minutes
        val end = System.currentTimeMillis() + durationMs
        _endAtEpochMs.value = end
        timerJob = scope.launch {
            while (isActive) {
                delay(400)
                if (System.currentTimeMillis() >= end) {
                    audioPlayerManager.pause()
                    _endAtEpochMs.value = null
                    _scheduledMinutes.value = null
                    timerJob = null
                    break
                }
            }
        }
    }

    fun cancel() {
        timerJob?.cancel()
        timerJob = null
        _endAtEpochMs.value = null
        _scheduledMinutes.value = null
    }
}
