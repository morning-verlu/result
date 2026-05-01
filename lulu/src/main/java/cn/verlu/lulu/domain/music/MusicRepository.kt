package cn.verlu.lulu.domain.music

import kotlinx.coroutines.flow.Flow

interface MusicRepository {
    fun observeLocalTracks(): Flow<List<LocalMusicTrack>>

    fun observeMiniPlayer(): Flow<MiniPlayerState>

    suspend fun scanLocalMusic()

    fun playTrack(track: LocalMusicTrack)

    fun togglePlayPause()
}
