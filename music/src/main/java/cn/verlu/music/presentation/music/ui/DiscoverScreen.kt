package cn.verlu.music.presentation.music.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.verlu.music.presentation.navigation.LocalOpenMusicDrawer
import cn.verlu.music.presentation.music.vm.DiscoverEvent
import cn.verlu.music.presentation.music.vm.DiscoverViewModel
import kotlinx.coroutines.delay

@Composable
fun DiscoverRoute(
    modifier: Modifier = Modifier,
    viewModel: DiscoverViewModel = hiltViewModel(),
    onNavigateToNowPlaying: () -> Unit,
    onNavigateToOnlineCatalog: () -> Unit,
    onNavigateToDownloadManager: () -> Unit
) {
    DiscoverScreen(
        modifier = modifier,
        viewModel = viewModel,
        onNavigateToNowPlaying = onNavigateToNowPlaying,
        onNavigateToOnlineCatalog = onNavigateToOnlineCatalog,
        onNavigateToDownloadManager = onNavigateToDownloadManager
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DiscoverScreen(
    modifier: Modifier = Modifier,
    viewModel: DiscoverViewModel,
    onNavigateToNowPlaying: () -> Unit,
    onNavigateToOnlineCatalog: () -> Unit,
    onNavigateToDownloadManager: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.refreshCatalogAvailability()
    }

    LaunchedEffect(state.scrollFeedToTop) {
        if (state.scrollFeedToTop) {
            delay(32)
            listState.scrollToItem(0)
            viewModel.consumeScrollFeedToTop()
        }
    }
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is DiscoverEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            LargeTopAppBar(
                navigationIcon = {
                    IconButton(onClick = LocalOpenMusicDrawer.current) {
                        Icon(Icons.Default.Menu, contentDescription = "菜单")
                    }
                },
                title = { Text("发现", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = onNavigateToDownloadManager) {
                        Icon(Icons.Default.DownloadForOffline, contentDescription = "下载管理")
                    }
                    IconButton(onClick = onNavigateToOnlineCatalog) {
                        Icon(Icons.Default.Search, contentDescription = "搜索")
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
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { viewModel.onPullRefresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (state.feedItems.isEmpty() && !state.isRefreshing) {
                if (!state.initialLoadFinished) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        MusicLoadingIndicator()
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "暂无可用歌曲，检查网络后下拉或点击重试",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = { viewModel.onPullRefresh() }) {
                            Text("重试")
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(state.feedItems, key = { it.id }) { row ->
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
                    if (state.isResolvingTrack) {
                        MusicLoadingIndicator(
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
            }
        }
    }
}
