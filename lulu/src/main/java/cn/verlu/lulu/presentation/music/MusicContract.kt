package cn.verlu.lulu.presentation.music

import cn.verlu.lulu.domain.music.LocalMusicTrack
import cn.verlu.lulu.domain.music.MiniPlayerState

object MusicContract {
    data class UiState(
        val localTracks: List<LocalMusicTrack> = emptyList(),
        val miniPlayerState: MiniPlayerState = MiniPlayerState(),
        val isScanning: Boolean = false,
    )
}
