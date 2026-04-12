package cn.verlu.music.presentation.music.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.verlu.music.data.local.dao.TrackDao
import cn.verlu.music.data.local.entity.FavoriteTrackEntity
import cn.verlu.music.data.repository.MusicCatalogRepository
import cn.verlu.music.data.repository.OnlineMusicDownloadRepository
import cn.verlu.music.domain.model.CatalogTrack
import cn.verlu.music.domain.model.LocalAudio
import cn.verlu.music.domain.player.AudioPlayerManager
import cn.verlu.music.domain.player.PlayerState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class DiscoverState(
    val catalogAvailable: Boolean = true,
    val feedItems: List<CatalogTrack> = emptyList(),
    val initialLoadFinished: Boolean = false,
    val isRefreshing: Boolean = false,
    val isResolvingTrack: Boolean = false,
    val favoriteIds: Set<Long> = emptySet(),
    val progress: Float = 0f,
    /** 下拉刷新完成后滚动到顶部（由 UI 消费后清除） */
    val scrollFeedToTop: Boolean = false
)

sealed interface DiscoverEvent {
    data class ShowMessage(val message: String) : DiscoverEvent
}

@HiltViewModel
class DiscoverViewModel @Inject constructor(
    private val catalogRepository: MusicCatalogRepository,
    private val trackDao: TrackDao,
    private val downloadRepository: OnlineMusicDownloadRepository,
    val audioPlayerManager: AudioPlayerManager
) : ViewModel() {

    private val _state = MutableStateFlow(DiscoverState())
    val state: StateFlow<DiscoverState> = _state.asStateFlow()
    private val _events = MutableSharedFlow<DiscoverEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<DiscoverEvent> = _events

    val playerState: StateFlow<PlayerState> = audioPlayerManager.playerState
    private var preheatJob: Job? = null

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
        loadInitialFeed()
    }

    fun refreshCatalogAvailability() {
        _state.update { it.copy(catalogAvailable = true) }
        if (_state.value.feedItems.isEmpty()) loadInitialFeed()
    }

    private fun loadInitialFeed() {
        viewModelScope.launch {
            val batch = runCatching {
                withContext(Dispatchers.IO) { catalogRepository.fetchDiscoverList(DISCOVER_BATCH) }
            }.getOrDefault(emptyList())
            _state.update {
                it.copy(
                    feedItems = batch,
                    initialLoadFinished = true,
                    scrollFeedToTop = batch.isNotEmpty()
                )
            }
        }
    }

    fun onPullRefresh() {
        viewModelScope.launch {
            _state.update { it.copy(isRefreshing = true) }
            try {
                val batch = withContext(Dispatchers.IO) { catalogRepository.fetchDiscoverList(DISCOVER_BATCH) }
                _state.update {
                    it.copy(
                        feedItems = batch,
                        isRefreshing = false,
                        scrollFeedToTop = batch.isNotEmpty()
                    )
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.update { it.copy(isRefreshing = false) }
            }
        }
    }

    fun consumeScrollFeedToTop() {
        _state.update { it.copy(scrollFeedToTop = false) }
    }

    fun playTrack(track: CatalogTrack) {
        viewModelScope.launch(Dispatchers.IO) {
            preheatJob?.cancel()
            _state.update { it.copy(isResolvingTrack = true) }
            try {
                val queue = _state.value.feedItems
                val startIndex = queue.indexOfFirst { it.id == track.id }
                if (startIndex < 0) return@launch
                val initial = resolvePlayableQueue(queue, startIndex, INITIAL_PLAYABLE_COUNT)
                val playable = initial.takeIf { it.isNotEmpty() }
                    ?: resolvePlayableQueue(queue, startIndex, PLAYABLE_QUEUE_SIZE)
                if (playable.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        audioPlayerManager.setPlaylistAndPlay(playable, 0)
                    }
                    preheatJob = viewModelScope.launch(Dispatchers.IO) {
                        val more = resolvePlayableQueue(queue, startIndex, PLAYABLE_QUEUE_SIZE)
                        val toAppend = more.drop(playable.size)
                        if (toAppend.isNotEmpty()) {
                            withContext(Dispatchers.Main) { audioPlayerManager.appendToQueue(toAppend) }
                        }
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
    ): List<LocalAudio> {
        val playable = ArrayList<LocalAudio>(maxCount)
        for (candidate in queue.drop(startIndex)) {
            if (playable.size >= maxCount) break
            runCatching {
                val resolved = catalogRepository.resolveTrack(candidate.rid)
                if (catalogRepository.validatePlayableUrl(resolved.url)) {
                    playable += candidate.toLocalAudio(url = resolved.url, lrc = resolved.lrc)
                }
            }
        }
        return playable
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
            try {
                _events.emit(DiscoverEvent.ShowMessage("正在解析下载链接..."))
                val resolved = catalogRepository.resolveTrack(track.rid)
                val downloadId = downloadRepository.enqueue(
                    title = track.song,
                    artist = track.singer,
                    url = resolved.url
                )
                _events.emit(DiscoverEvent.ShowMessage("已加入下载队列（ID: $downloadId）"))
            } catch (_: IllegalArgumentException) {
                _events.emit(DiscoverEvent.ShowMessage("下载链接无效，无法下载"))
            } catch (e: Exception) {
                _events.emit(DiscoverEvent.ShowMessage("下载失败：${e.message ?: "未知错误"}"))
            }
        }
    }

    private companion object {
        const val DISCOVER_BATCH = 80
        const val PLAYABLE_QUEUE_SIZE = 12
        const val INITIAL_PLAYABLE_COUNT = 3
    }
}
