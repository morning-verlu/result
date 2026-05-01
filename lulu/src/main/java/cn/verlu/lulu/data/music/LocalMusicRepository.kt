package cn.verlu.lulu.data.music

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import cn.verlu.lulu.di.IoDispatcher
import cn.verlu.lulu.domain.music.LocalMusicTrack
import cn.verlu.lulu.domain.music.MiniPlayerState
import cn.verlu.lulu.domain.music.MusicRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

@Singleton
class LocalMusicRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : MusicRepository {
    private val tracks = MutableStateFlow<List<LocalMusicTrack>>(emptyList())
    private val miniPlayer = MutableStateFlow(MiniPlayerState())
    private val mainHandler = Handler(Looper.getMainLooper())

    private var currentTrack: LocalMusicTrack? = null

    private val player: ExoPlayer = ExoPlayer.Builder(context).build().also { exo ->
        exo.addListener(
            object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    syncMiniFromPlayer()
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    syncMiniFromPlayer()
                }
            },
        )
    }

    override fun observeLocalTracks(): Flow<List<LocalMusicTrack>> = tracks.asStateFlow()

    override fun observeMiniPlayer(): Flow<MiniPlayerState> = miniPlayer.asStateFlow()

    override suspend fun scanLocalMusic() {
        val list = withContext(ioDispatcher) { queryAudioTracks() }
        tracks.value = list
    }

    override fun playTrack(track: LocalMusicTrack) {
        runOnMain {
            currentTrack = track
            player.setMediaItem(MediaItem.fromUri(Uri.parse(track.uri)))
            player.prepare()
            player.playWhenReady = true
            miniPlayer.value = MiniPlayerState(track = track, isPlaying = player.isPlaying)
        }
    }

    override fun togglePlayPause() {
        runOnMain {
            val t = currentTrack ?: return@runOnMain
            if (player.isPlaying) {
                player.pause()
            } else {
                if (player.mediaItemCount == 0) {
                    player.setMediaItem(MediaItem.fromUri(Uri.parse(t.uri)))
                    player.prepare()
                }
                player.playWhenReady = true
            }
            miniPlayer.value = MiniPlayerState(track = t, isPlaying = player.isPlaying)
        }
    }

    private fun syncMiniFromPlayer() {
        val t = currentTrack ?: return
        runOnMain {
            miniPlayer.value = MiniPlayerState(
                track = t,
                isPlaying = player.isPlaying && player.playbackState != Player.STATE_IDLE,
            )
        }
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    private fun queryAudioTracks(): List<LocalMusicTrack> {
        val result = mutableListOf<LocalMusicTrack>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
        )
        val selection =
            "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 10000"
        val sort = "${MediaStore.Audio.Media.TITLE} ASC"

        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            sort,
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val uri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    id,
                ).toString()
                val durationMs = cursor.getLong(durationCol)
                result += LocalMusicTrack(
                    id = id.toString(),
                    title = cursor.getString(titleCol) ?: "未知标题",
                    artist = cursor.getString(artistCol),
                    album = cursor.getString(albumCol),
                    duration = if (durationMs > 0) Duration.ofMillis(durationMs) else null,
                    uri = uri,
                )
            }
        }
        return result
    }
}
