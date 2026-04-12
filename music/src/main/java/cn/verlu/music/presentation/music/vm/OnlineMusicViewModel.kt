package cn.verlu.music.presentation.music.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.verlu.music.data.local.dao.TrackDao
import cn.verlu.music.data.local.entity.FavoriteTrackEntity
import cn.verlu.music.data.repository.MusicCatalogRepository
import cn.verlu.music.data.repository.OnlineMusicDownloadRepository
import cn.verlu.music.data.repository.QqApiException
import cn.verlu.music.domain.model.CatalogTrack
import cn.verlu.music.domain.model.LocalAudio
import cn.verlu.music.domain.player.AudioPlayerManager
import cn.verlu.music.domain.player.PlayerState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class RetryAction { SEARCH, PLAY_LAST }

data class OnlineMusicState(
    val catalogAvailable: Boolean = true,
    val query: String = "",
    val results: List<CatalogTrack> = emptyList(),
    val isSearching: Boolean = false,
    val isResolvingTrack: Boolean = false,
    val favoriteIds: Set<Long> = emptySet(),
    val progress: Float = 0f
)

sealed interface OnlineMusicEvent {
    data class ShowMessage(val message: String) : OnlineMusicEvent
    data class ShowRetryableMessage(val message: String, val action: RetryAction) : OnlineMusicEvent
}

@HiltViewModel
class OnlineMusicViewModel @Inject constructor(
    private val catalogRepository: MusicCatalogRepository,
    private val trackDao: TrackDao,
    private val downloadRepository: OnlineMusicDownloadRepository,
    val audioPlayerManager: AudioPlayerManager
) : ViewModel() {

    private val _state = MutableStateFlow(OnlineMusicState())
    val state: StateFlow<OnlineMusicState> = _state.asStateFlow()
    private val _events = MutableSharedFlow<OnlineMusicEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<OnlineMusicEvent> = _events

    val playerState: StateFlow<PlayerState> = audioPlayerManager.playerState

    private var searchJob: Job? = null
    private var playJob: Job? = null
    private var preheatJob: Job? = null
    private var lastPlayClickAtMs: Long = 0L
    private var lastSearchedQuery: String = ""
    private var lastPlayTrackId: Long? = null
    private val recentDownloadAt = LinkedHashMap<String, Long>()

    init {
        viewModelScope.launch {
            trackDao.observeFavoriteMediaIds().collect { ids ->
                _state.update { it.copy(favoriteIds = ids.toSet()) }
            }
        }
        viewModelScope.launch {
            while (true) {
                val ps = audioPlayerManager.playerState.value
                if (ps.currentTrack != null) {
                    val dur = audioPlayerManager.mediaDurationMs()
                    val pos = audioPlayerManager.currentPositionMs()
                    if (dur > 0) {
                        _state.update { it.copy(progress = pos.toFloat() / dur.toFloat()) }
                    }
                } else {
                    _state.update { it.copy(progress = 0f) }
                }
                delay(1000)
            }
        }
        viewModelScope.launch {
            audioPlayerManager.playerErrors.collect { message ->
                _events.emit(OnlineMusicEvent.ShowMessage(message))
            }
        }
    }

    fun refreshCatalogAvailability() {
        _state.update { it.copy(catalogAvailable = true) }
    }

    fun onSearchQueryChange(raw: String) {
        _state.update { it.copy(query = raw) }
        searchJob?.cancel()
        val q = raw.trim()
        if (q.isEmpty()) {
            lastSearchedQuery = ""
            _state.update { it.copy(results = emptyList(), isSearching = false) }
            return
        }
        if (q == lastSearchedQuery) {
            return
        }
        searchJob = viewModelScope.launch {
            delay(320)
            _state.update { it.copy(isSearching = true) }
            try {
                val list = withContext(Dispatchers.IO) { catalogRepository.search(q) }
                lastSearchedQuery = q
                _state.update { it.copy(results = list, isSearching = false) }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.update { it.copy(isSearching = false) }
                emitMappedError("搜索失败", e, RetryAction.SEARCH)
            }
        }
    }

    fun playTrack(track: CatalogTrack) {
        val now = System.currentTimeMillis()
        if (now - lastPlayClickAtMs < PLAY_CLICK_DEBOUNCE_MS) return
        lastPlayClickAtMs = now
        playJob?.cancel()
        preheatJob?.cancel()
        val queue = _state.value.results
        val startIndex = queue.indexOfFirst { it.id == track.id }
        if (startIndex < 0) return
        lastPlayTrackId = track.id

        playJob = viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isResolvingTrack = true) }
            try {
                val (initialPlayable, skipped) = resolvePlayableQueue(queue, startIndex, INITIAL_PLAYABLE_COUNT)
                val playableQueue = initialPlayable.takeIf { it.isNotEmpty() }
                    ?: resolvePlayableQueue(queue, startIndex, PLAYABLE_QUEUE_SIZE).first
                if (playableQueue.isEmpty()) {
                    _events.emit(
                        OnlineMusicEvent.ShowRetryableMessage(
                            message = "当前及后续歌曲地址不可用，请稍后重试",
                            action = RetryAction.PLAY_LAST
                        )
                    )
                    return@launch
                }
                withContext(Dispatchers.Main) {
                    audioPlayerManager.setPlaylistAndPlay(playableQueue, 0)
                }
                if (skipped > 0) {
                    _events.emit(OnlineMusicEvent.ShowMessage("已自动跳过 $skipped 首不可播放歌曲"))
                }
                preheatJob = viewModelScope.launch(Dispatchers.IO) {
                    val extra = resolvePlayableQueue(queue, startIndex, PLAYABLE_QUEUE_SIZE).first
                    val toAppend = extra.drop(playableQueue.size)
                    if (toAppend.isNotEmpty()) {
                        withContext(Dispatchers.Main) { audioPlayerManager.appendToQueue(toAppend) }
                    }
                }
            } finally {
                _state.update { it.copy(isResolvingTrack = false) }
            }
        }
    }

    private fun resolvePlayableQueue(
        queue: List<CatalogTrack>,
        startIndex: Int,
        maxCount: Int
    ): Pair<List<LocalAudio>, Int> {
        var skipped = 0
        val playable = ArrayList<LocalAudio>(maxCount)
        val candidates = queue.drop(startIndex)
        for (candidate in candidates) {
            if (playable.size >= maxCount) break
            try {
                val resolved = catalogRepository.resolveTrack(candidate.rid)
                if (!catalogRepository.validatePlayableUrl(resolved.url)) {
                    skipped++
                    continue
                }
                playable += candidate.toLocalAudio(url = resolved.url, lrc = resolved.lrc)
            } catch (_: Exception) {
                skipped++
            }
        }
        return playable to skipped
    }

    fun toggleFavorite(track: CatalogTrack) {
        val id = -track.id
        viewModelScope.launch {
            if (_state.value.favoriteIds.contains(id)) {
                trackDao.deleteFavoriteTrack(id)
            } else {
                trackDao.insertFavoriteTrack(FavoriteTrackEntity(mediaId = id))
            }
        }
    }

    fun downloadTrack(track: CatalogTrack) {
        viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val lastAt = synchronized(recentDownloadAt) { recentDownloadAt[track.rid] }
            if (lastAt != null && now - lastAt < DOWNLOAD_DEDUPE_WINDOW_MS) {
                _events.emit(OnlineMusicEvent.ShowMessage("该歌曲刚刚已发起下载，请稍候"))
                return@launch
            }
            try {
                val resolved = catalogRepository.resolveTrack(track.rid)
                val downloadId = downloadRepository.enqueue(
                    title = track.song,
                    artist = track.singer,
                    url = resolved.url
                )
                synchronized(recentDownloadAt) {
                    recentDownloadAt[track.rid] = now
                    if (recentDownloadAt.size > 200) {
                        val firstKey = recentDownloadAt.entries.firstOrNull()?.key
                        if (firstKey != null) recentDownloadAt.remove(firstKey)
                    }
                }
                _events.emit(OnlineMusicEvent.ShowMessage("已加入下载队列（ID: $downloadId）"))
            } catch (_: IllegalArgumentException) {
                _events.emit(OnlineMusicEvent.ShowMessage("下载链接无效，无法下载"))
            } catch (e: Exception) {
                emitMappedError("下载失败", e, null)
            }
        }
    }

    fun retry(action: RetryAction) {
        when (action) {
            RetryAction.SEARCH -> onSearchQueryChange(_state.value.query)
            RetryAction.PLAY_LAST -> {
                val id = lastPlayTrackId ?: return
                val track = _state.value.results.firstOrNull { it.id == id } ?: return
                playTrack(track)
            }
        }
    }

    private suspend fun emitMappedError(prefix: String, e: Exception, retryAction: RetryAction?) {
        val mapped = if (e is QqApiException) e else QqApiException(e.message ?: "未知错误", retryable = false, cause = e)
        val msg = "$prefix：${mapped.userMessage}"
        if (retryAction != null && mapped.retryable) {
            _events.emit(OnlineMusicEvent.ShowRetryableMessage(msg, retryAction))
        } else {
            _events.emit(OnlineMusicEvent.ShowMessage(msg))
        }
    }

    private companion object {
        const val PLAY_CLICK_DEBOUNCE_MS = 650L
        const val PLAYABLE_QUEUE_SIZE = 12
        const val INITIAL_PLAYABLE_COUNT = 3
        const val DOWNLOAD_DEDUPE_WINDOW_MS = 5_000L
    }
}
