package cn.verlu.music.domain.player

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.HttpDataSource
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import cn.verlu.music.domain.model.LocalAudio
import cn.verlu.music.service.MusicPlayerService
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

enum class PlaybackMode {
    SEQUENCE,   // 顺序播放
    REPEAT_ONE, // 单曲循环
    SHUFFLE     // 随机播放
}

data class PlayerState(
    val currentTrack: LocalAudio? = null,
    val isPlaying: Boolean = false,
    val queue: List<LocalAudio> = emptyList(),
    val playbackMode: PlaybackMode = PlaybackMode.SEQUENCE,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L
)

@Singleton
class AudioPlayerManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // MediaController is the client-side proxy to MusicPlayerService's ExoPlayer.
    // All playback commands go through this controller, which communicates with the
    // service over IPC — the service keeps playing even when the app is in the background.
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    // Keep a direct ExoPlayer handle for position queries (read-only, short-lived access).
    // The controller IS a Player, so we use it directly.
    val player: Player? get() = controller

    // Legacy name kept for compatibility with UI that calls audioPlayerManager.exoPlayer
    val exoPlayer: Player get() = controller ?: throw IllegalStateException("Controller not ready")

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _playerErrors = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val playerErrors: SharedFlow<String> = _playerErrors

    private val executor = Executors.newSingleThreadExecutor()
    private var progressJob: kotlin.jvm.Volatile? = null // Using direct polling in VM or helper instead?
    // Actually, Media3 doesn't have a built-in progress callback.
    // I'll add a helper flow or just keep it simple.

    init {
        connectToService()
    }

    private fun connectToService() {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, MusicPlayerService::class.java)
        )
        controllerFuture = MediaController.Builder(context, sessionToken)
            .buildAsync()

        controllerFuture?.addListener({
            try {
                controller = controllerFuture?.get()
                controller?.addListener(object : Player.Listener {
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        val id = mediaItem?.mediaId?.toLongOrNull()
                        val prev = _playerState.value.currentTrack
                        val queue = _playerState.value.queue
                        val track = id?.let { qid -> queue.find { it.id == qid } }

                        // 仅时间轴/播放列表内部调整且仍是同一首时，不要清空曲目或把进度写成 0，避免 UI 与后续逻辑误判
                        if (id != null && id == prev?.id &&
                            reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED
                        ) {
                            return
                        }
                        // mediaId 与当前曲一致但队列快照里找不到（极少见），保留 currentTrack，避免误走「重建播放列表」分支
                        if (track == null && id != null && id == prev?.id) {
                            return
                        }

                        if (track != null) {
                            _playerState.update {
                                it.copy(currentTrack = track, currentPositionMs = 0L)
                            }
                        } else if (id == null) {
                            _playerState.update {
                                it.copy(currentTrack = null, currentPositionMs = 0L)
                            }
                        } else {
                            _playerState.update { it.copy(currentTrack = null) }
                        }
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _playerState.update { it.copy(isPlaying = isPlaying) }
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        val ctrl = controller ?: return
                        if (playbackState == Player.STATE_READY) {
                            _playerState.update { it.copy(durationMs = ctrl.duration.coerceAtLeast(0L)) }
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        scope.launch {
                            _playerErrors.emit(mapPlaybackError(error))
                        }
                    }
                })
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, executor)
    }

    // Helper to get current progress ratio
    fun getProgress(): Float {
        val ctrl = controller ?: return 0f
        val dur = ctrl.duration
        val pos = ctrl.currentPosition
        return if (dur > 0) pos.toFloat() / dur.toFloat() else 0f
    }

    fun setPlaylistAndPlay(tracks: List<LocalAudio>, startIndex: Int) {
        _playerState.update { it.copy(queue = tracks) }
        val ctrl = controller ?: return
        val mediaItems = tracks.map(::mediaItemFromAudio)
        ctrl.setMediaItems(mediaItems)
        ctrl.prepare()
        if (startIndex in tracks.indices) {
            ctrl.seekToDefaultPosition(startIndex)
        }
        applyPlaybackModeToPlayer(_playerState.value.playbackMode)
        ctrl.play()
    }

    /** 在当前队列末尾追加曲目，用于后台预热在线可播列表。 */
    fun appendToQueue(tracks: List<LocalAudio>) {
        if (tracks.isEmpty()) return
        val ctrl = controller ?: return
        val oldQueue = _playerState.value.queue
        val dedup = tracks.filter { newItem -> oldQueue.none { it.id == newItem.id } }
        if (dedup.isEmpty()) return
        ctrl.addMediaItems(dedup.map(::mediaItemFromAudio))
        _playerState.update { it.copy(queue = oldQueue + dedup) }
    }

    fun togglePlayPause() {
        val ctrl = controller ?: return
        if (ctrl.isPlaying) ctrl.pause() else ctrl.play()
    }

    fun next() {
        val ctrl = controller ?: return
        if (ctrl.hasNextMediaItem()) ctrl.seekToNextMediaItem()
    }

    fun previous() {
        val ctrl = controller ?: return
        if (ctrl.hasPreviousMediaItem()) ctrl.seekToPreviousMediaItem()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
    }

    fun currentPosition(): Long = controller?.currentPosition ?: 0L

    /** 与 [currentPosition] 相同语义，供 ViewModel 轮询时间轴，避免 UI 直接拿 [player]。 */
    fun currentPositionMs(): Long {
        val p = controller?.currentPosition ?: return 0L
        if (p == C.TIME_UNSET) return 0L
        return p.coerceAtLeast(0L)
    }

    fun mediaDurationMs(): Long {
        val d = controller?.duration ?: return 0L
        if (d == C.TIME_UNSET) return 0L
        return d.coerceAtLeast(0L)
    }

    fun pause() = controller?.pause()
    fun play() = controller?.play()

    /** Exo 侧是否仍有媒体队列（比仅看 [PlayerState] 更可靠，避免状态不同步时误重建列表）。 */
    fun hasActiveQueue(): Boolean = (controller?.mediaItemCount ?: 0) > 0

    fun isControllerPlaying(): Boolean = controller?.isPlaying == true

    fun cyclePlaybackMode() {
        val nextMode = when (_playerState.value.playbackMode) {
            PlaybackMode.SEQUENCE -> PlaybackMode.REPEAT_ONE
            PlaybackMode.REPEAT_ONE -> PlaybackMode.SHUFFLE
            PlaybackMode.SHUFFLE -> PlaybackMode.SEQUENCE
        }
        _playerState.update { it.copy(playbackMode = nextMode) }
        applyPlaybackModeToPlayer(nextMode)
    }

    private fun applyPlaybackModeToPlayer(mode: PlaybackMode) {
        val ctrl = controller ?: return
        when (mode) {
            PlaybackMode.SEQUENCE -> {
                ctrl.repeatMode = Player.REPEAT_MODE_ALL
                ctrl.shuffleModeEnabled = false
            }
            PlaybackMode.REPEAT_ONE -> {
                ctrl.repeatMode = Player.REPEAT_MODE_ONE
                ctrl.shuffleModeEnabled = false
            }
            PlaybackMode.SHUFFLE -> {
                ctrl.repeatMode = Player.REPEAT_MODE_ALL
                ctrl.shuffleModeEnabled = true
            }
        }
    }

    private fun mapPlaybackError(error: PlaybackException): String {
        val cause = error.cause
        if (cause is HttpDataSource.InvalidResponseCodeException) {
            return when (cause.responseCode) {
                410 -> "音源已失效（410），请刷新歌曲列表或更换音源"
                404 -> "音源不存在（404），请切换到其他歌曲"
                403 -> "音源无访问权限（403）"
                else -> "网络音源请求失败（HTTP ${cause.responseCode}）"
            }
        }
        return "播放失败：${error.message ?: "未知错误"}"
    }

    private fun mediaItemFromAudio(audio: LocalAudio): MediaItem {
        return MediaItem.Builder()
            .setUri(audio.uri)
            .setMediaId(audio.id.toString())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(audio.title)
                    .setArtist(audio.artist)
                    .setAlbumTitle(audio.album)
                    .build()
            )
            .build()
    }

    fun release() {
        MediaController.releaseFuture(controllerFuture ?: return)
        scope.coroutineContext.cancel()
        executor.shutdown()
    }
}
