package cn.verlu.music.presentation.music.vm

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.verlu.music.R
import cn.verlu.music.data.repository.MusicCatalogRepository
import cn.verlu.music.data.repository.PlayerCacheRepository
import cn.verlu.music.domain.timer.SleepTimerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class MusicDrawerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sleepTimerManager: SleepTimerManager,
    private val playerCacheRepository: PlayerCacheRepository,
    private val musicCatalogRepository: MusicCatalogRepository
) : ViewModel() {

    val endAtEpochMs = sleepTimerManager.endAtEpochMs
    val scheduledSleepMinutes = sleepTimerManager.scheduledMinutes

    private val _cacheBytes = MutableStateFlow(playerCacheRepository.usedBytes())
    val cacheBytes: StateFlow<Long> = _cacheBytes.asStateFlow()
    private val _resolveCacheCount = MutableStateFlow(musicCatalogRepository.resolveCacheCount())
    val resolveCacheCount: StateFlow<Int> = _resolveCacheCount.asStateFlow()

    fun refreshCacheSize() {
        _cacheBytes.value = playerCacheRepository.usedBytes()
        _resolveCacheCount.value = musicCatalogRepository.resolveCacheCount()
    }

    fun setSleepTimerMinutes(minutes: Int) {
        if (minutes <= 0) sleepTimerManager.cancel()
        else sleepTimerManager.scheduleStopAfter(minutes * 60_000L)
    }

    fun cancelSleepTimer() {
        sleepTimerManager.cancel()
    }

    fun clearPlaybackCache(onDone: (String) -> Unit) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { playerCacheRepository.clearAll() }
                _cacheBytes.value = playerCacheRepository.usedBytes()
                onDone(context.getString(R.string.drawer_cleared_audio))
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                onDone(
                    context.getString(
                        R.string.drawer_clear_failed,
                        e.message ?: "未知错误"
                    )
                )
            }
        }
    }

    fun clearResolveCache(onDone: (String) -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { musicCatalogRepository.clearResolveCache() }
            _resolveCacheCount.value = musicCatalogRepository.resolveCacheCount()
            onDone("已清理歌词/解析缓存")
        }
    }
}
