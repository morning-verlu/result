package cn.verlu.music.presentation.music.vm

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.verlu.music.data.local.dao.TrackDao
import cn.verlu.music.data.local.entity.FavoriteTrackEntity
import cn.verlu.music.data.local.entity.HiddenTrackEntity
import cn.verlu.music.data.repository.LocalMediaRepository
import cn.verlu.music.domain.model.LocalAudio
import cn.verlu.music.domain.player.AudioPlayerManager
import cn.verlu.music.domain.player.PlayerState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LocalMusicState(
    val isLoading: Boolean = false,
    val tracks: List<LocalAudio> = emptyList(),
    val hiddenTracks: List<LocalAudio> = emptyList(),
    val favoriteIds: Set<Long> = emptySet(),
    val progress: Float = 0f,
    val error: String? = null
)

@HiltViewModel
class LocalMusicViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: LocalMediaRepository,
    private val trackDao: TrackDao,
    val audioPlayerManager: AudioPlayerManager
) : ViewModel() {

    private val _state = MutableStateFlow(LocalMusicState())
    val state: StateFlow<LocalMusicState> = _state.asStateFlow()

    val playerState: StateFlow<PlayerState> = audioPlayerManager.playerState

    private val _playbackPositionMs = MutableStateFlow(0L)
    val playbackPositionMs: StateFlow<Long> = _playbackPositionMs.asStateFlow()

    private val _playbackDurationMs = MutableStateFlow(0L)
    val playbackDurationMs: StateFlow<Long> = _playbackDurationMs.asStateFlow()
    private var hasLoadedOnce = false

    init {
        viewModelScope.launch {
            trackDao.observeFavoriteMediaIds().collect { favIds ->
                _state.update { it.copy(favoriteIds = favIds.toSet()) }
            }
        }
        // 时间轴与迷你播放器进度：ViewModel 内每秒轮询 MediaController，UI 只读 StateFlow（不在 Composable 里轮询）
        viewModelScope.launch {
            while (true) {
                val ps = audioPlayerManager.playerState.value
                if (ps.currentTrack != null) {
                    val dur = audioPlayerManager.mediaDurationMs()
                    val pos = audioPlayerManager.currentPositionMs()
                    _playbackDurationMs.value = dur
                    _playbackPositionMs.value = pos
                    if (dur > 0) {
                        _state.update { it.copy(progress = pos.toFloat() / dur.toFloat()) }
                    }
                } else {
                    _playbackDurationMs.value = 0L
                    _playbackPositionMs.value = 0L
                    _state.update { it.copy(progress = 0f) }
                }
                kotlinx.coroutines.delay(1000)
            }
        }
    }

    /** 仅首次进入（或尚无数据）时加载，避免每次切页都触发「刷新态」 */
    fun ensureMusicLoaded() {
        if (hasLoadedOnce && _state.value.tracks.isNotEmpty()) return
        loadMusic()
    }

    fun loadMusic() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val dbHiddenIds = trackDao.getAllHiddenMediaIds().toSet()
                val allAudio = repository.getAllAudioFilesRaw()
                val filtered = allAudio.filter { it.id !in dbHiddenIds }
                hasLoadedOnce = true
                _state.update { it.copy(isLoading = false, tracks = filtered) }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.update { it.copy(isLoading = false, error = e.localizedMessage) }
            }
        }
    }

    fun loadHiddenTracks() {
        viewModelScope.launch {
            try {
                val hiddenIds = trackDao.getAllHiddenMediaIds()
                val allFiles = repository.getAllAudioFilesRaw()
                val hidden = allFiles.filter { hiddenIds.contains(it.id) }
                _state.update { it.copy(hiddenTracks = hidden) }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                // ignore others
            }
        }
    }

    fun playTrack(audio: LocalAudio) {
        val files = state.value.tracks
        val index = files.indexOfFirst { it.id == audio.id }
        if (index >= 0) {
            audioPlayerManager.setPlaylistAndPlay(files, index)
        }
    }

    /**
     * 有在播队列/当前曲则继续（暂停则恢复；已在播则交给 [onAlreadyPlaying] 例如打开正在播放页）；
     * 否则用当前列表从第一首顺序播放。
     */
    fun continueOrPlayFromLibrary(onAlreadyPlaying: () -> Unit = {}) {
        val tracks = state.value.tracks
        if (tracks.isEmpty()) return
        val ps = audioPlayerManager.playerState.value
        when {
            ps.currentTrack != null -> {
                if (!audioPlayerManager.isControllerPlaying()) {
                    audioPlayerManager.play()
                } else {
                    onAlreadyPlaying()
                }
            }
            // 状态里丢当前曲但 Exo 仍有队列时，只能恢复播放，绝不能 setPlaylistAndPlay（会 prepare 导致从头播）
            audioPlayerManager.hasActiveQueue() -> {
                if (!audioPlayerManager.isControllerPlaying()) {
                    audioPlayerManager.play()
                } else {
                    onAlreadyPlaying()
                }
            }
            ps.queue.isNotEmpty() -> audioPlayerManager.play()
            else -> audioPlayerManager.setPlaylistAndPlay(tracks, 0)
        }
    }

    fun hideTracks(ids: Set<Long>) {
        viewModelScope.launch {
            ids.forEach { id -> trackDao.insertHiddenTrack(HiddenTrackEntity(mediaId = id)) }
            loadMusic()
        }
    }

    fun restoreHiddenTrack(id: Long) {
        viewModelScope.launch {
            trackDao.unhideTracks(listOf(id))
            loadHiddenTracks()
            loadMusic()
        }
    }

    fun toggleFavorite(id: Long) {
        viewModelScope.launch {
            if (_state.value.favoriteIds.contains(id)) {
                trackDao.deleteFavoriteTrack(id)
            } else {
                trackDao.insertFavoriteTrack(FavoriteTrackEntity(mediaId = id))
            }
        }
    }

    /** Called when user picks audio files via SAF (Storage Access Framework) file picker */
    fun importTracks(uris: List<Uri>) {
        viewModelScope.launch {
            val imported = uris.mapIndexed { i, uri ->
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {}

                val rawName = uri.lastPathSegment?.substringAfterLast('/')
                    ?.substringAfterLast('%') ?: "Unknown"
                val cleanName = rawName
                    .removeSuffix(".mp3").removeSuffix(".flac")
                    .removeSuffix(".m4a").removeSuffix(".wav").removeSuffix(".ogg")

                LocalAudio(
                    id = uri.hashCode().toLong() + i,
                    uri = uri,
                    title = cleanName,
                    artist = "Unknown Artist",
                    album = "Unknown Album",
                    durationMs = 0L,
                    albumId = 0L
                )
            }
            val merged = (state.value.tracks + imported).distinctBy { it.uri.toString() }
            _state.update { it.copy(tracks = merged) }
            // 已在播放时不要重建队列；仅冷启动无任何媒体时自动建列表并暂停在首首
            if (!audioPlayerManager.hasActiveQueue() && merged.isNotEmpty()) {
                audioPlayerManager.setPlaylistAndPlay(merged, 0)
                audioPlayerManager.pause()
            }
        }
    }
}
