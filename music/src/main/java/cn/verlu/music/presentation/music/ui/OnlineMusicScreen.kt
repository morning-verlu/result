package cn.verlu.music.presentation.music.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import kotlinx.coroutines.delay
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.verlu.music.presentation.music.vm.OnlineMusicEvent
import cn.verlu.music.presentation.music.vm.OnlineMusicViewModel

@Composable
fun OnlineMusicRoute(
    modifier: Modifier = Modifier,
    viewModel: OnlineMusicViewModel = hiltViewModel(),
    onNavigateUp: () -> Unit,
    onNavigateToNowPlaying: () -> Unit
) {
    OnlineMusicScreen(
        modifier = modifier,
        viewModel = viewModel,
        onNavigateUp = onNavigateUp,
        onNavigateToNowPlaying = onNavigateToNowPlaying
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun OnlineMusicScreen(
    modifier: Modifier = Modifier,
    viewModel: OnlineMusicViewModel,
    onNavigateUp: () -> Unit,
    onNavigateToNowPlaying: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.refreshCatalogAvailability()
    }

    LaunchedEffect(Unit) {
        delay(120)
        focusRequester.requestFocus()
        keyboard?.show()
    }
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is OnlineMusicEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
                is OnlineMusicEvent.ShowRetryableMessage -> {
                    val result = snackbarHostState.showSnackbar(
                        message = event.message,
                        actionLabel = "重试"
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.retry(event.action)
                    }
                }
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("搜索", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                }
            )
        },
        bottomBar = {
            val track = playerState.currentTrack
            AnimatedVisibility(visible = track != null) {
                if (track != null) {
                    PlayerBottomBar(
                        track = track,
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TextField(
                    value = state.query,
                    onValueChange = viewModel::onSearchQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .focusRequester(focusRequester),
                    placeholder = {
                        Text(
                            "搜索歌名或歌手",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                        errorIndicatorColor = Color.Transparent,
                        cursorColor = MaterialTheme.colorScheme.primary,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    ),
                    trailingIcon = {
                        if (state.query.isNotEmpty()) {
                            IconButton(onClick = {
                                viewModel.onSearchQueryChange("")
                                focusManager.clearFocus()
                            }) {
                                Icon(Icons.Default.Clear, contentDescription = "清除")
                            }
                        }
                    }
                )

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.query.isBlank() -> {
                        Text(
                            "在上方输入关键词，在线搜索歌曲",
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    state.isSearching && state.results.isEmpty() -> {
                        MusicLoadingIndicator(
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }

                    !state.isSearching && state.results.isEmpty() -> {
                        Text(
                            "无结果",
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            items(state.results, key = { it.id }) { row ->
                                val localId = -row.id
                                CatalogTrackRow(
                                    track = row,
                                    isPlaying = playerState.currentTrack?.id == localId,
                                    isFavorite = state.favoriteIds.contains(localId),
                                    onFavoriteToggle = { viewModel.toggleFavorite(row) },
                                    onDownload = { viewModel.downloadTrack(row) },
                                    downloadIcon = Icons.Default.Download,
                                    onClick = { viewModel.playTrack(row) }
                                )
                            }
                        }
                    }
                }
                if (state.isResolvingTrack) {
                    MusicLoadingIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    }
}
