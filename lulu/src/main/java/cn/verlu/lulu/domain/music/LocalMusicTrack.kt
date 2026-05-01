package cn.verlu.lulu.domain.music

import java.time.Duration

data class LocalMusicTrack(
    val id: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val duration: Duration?,
    val uri: String,
)

data class MiniPlayerState(
    val track: LocalMusicTrack? = null,
    val isPlaying: Boolean = false,
)
