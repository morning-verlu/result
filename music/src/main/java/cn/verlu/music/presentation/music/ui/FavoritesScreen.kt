package cn.verlu.music.presentation.music.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.verlu.music.presentation.navigation.LocalOpenMusicDrawer
import cn.verlu.music.presentation.music.vm.LocalMusicViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesRoute(
    modifier: Modifier = Modifier,
    viewModel: LocalMusicViewModel = hiltViewModel(),
    onNavigateToNowPlaying: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()

    val favoriteTracks = state.tracks.filter { state.favoriteIds.contains(it.id) }

    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }

    LaunchedEffect(Unit) {
        viewModel.loadMusic()  // Ensure tracks are loaded so we can filter favorites
    }

    BackHandler(enabled = selectionMode) {
        selectionMode = false
        selectedIds = emptySet()
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                navigationIcon = {
                    IconButton(onClick = LocalOpenMusicDrawer.current) {
                        Icon(Icons.Default.Menu, contentDescription = "菜单")
                    }
                },
                title = { Text("我的收藏 (${favoriteTracks.size})") },
                scrollBehavior = scrollBehavior
            )
        },
        bottomBar = {
            val currentTrack = playerState.currentTrack
            AnimatedVisibility(visible = !selectionMode && currentTrack != null) {
                if (currentTrack != null) {
                    PlayerBottomBar(
                        track = currentTrack,
                        isPlaying = playerState.isPlaying,
                        progress = state.progress,
                        onPlayPause = { viewModel.audioPlayerManager.togglePlayPause() },
                        onNext = { viewModel.audioPlayerManager.next() },
                        onPrevious = { viewModel.audioPlayerManager.previous() },
                        onBarClick = onNavigateToNowPlaying
                    )
                }
            }
        }
    ) { innerPadding ->
        if (favoriteTracks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "还没有收藏的音乐，点击 ♡ 收藏吧",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(favoriteTracks, key = { it.id }) { track ->
                    val isSelected = selectedIds.contains(track.id)
                    TrackItem(
                        track = track,
                        isPlaying = playerState.currentTrack?.id == track.id,
                        selectionMode = selectionMode,
                        isSelected = isSelected,
                        isFavorite = true,
                        onFavoriteToggle = { viewModel.toggleFavorite(track.id) },
                        onClick = {
                            if (selectionMode) {
                                if (isSelected) selectedIds -= track.id else selectedIds += track.id
                            } else {
                                // Play only from favorites playlist
                                val index = favoriteTracks.indexOf(track)
                                if (index >= 0) {
                                    viewModel.audioPlayerManager.setPlaylistAndPlay(
                                        favoriteTracks,
                                        index
                                    )
                                }
                            }
                        },
                        onLongClick = {
                            if (!selectionMode) {
                                selectionMode = true
                                selectedIds = setOf(track.id)
                            }
                        }
                    )
                }
            }
        }
    }
}
