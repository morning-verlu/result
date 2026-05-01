package cn.verlu.lulu.presentation.music

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.verlu.lulu.domain.music.LocalMusicTrack
import cn.verlu.lulu.domain.music.MusicRepository
import cn.verlu.lulu.presentation.music.MusicContract.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class MusicViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
) : ViewModel() {
    private val isScanning = MutableStateFlow(false)

    val state: StateFlow<UiState> =
        combine(
            musicRepository.observeLocalTracks(),
            musicRepository.observeMiniPlayer(),
            isScanning,
        ) { localTracks, miniPlayerState, scanning ->
            UiState(
                localTracks = localTracks,
                miniPlayerState = miniPlayerState,
                isScanning = scanning,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UiState(),
        )

    fun scanLocalMusic() {
        if (isScanning.value) return
        viewModelScope.launch {
            isScanning.value = true
            try {
                musicRepository.scanLocalMusic()
            } finally {
                isScanning.value = false
            }
        }
    }

    fun playTrack(track: LocalMusicTrack) {
        musicRepository.playTrack(track)
    }

    fun togglePlayPause() {
        musicRepository.togglePlayPause()
    }
}
