package cn.verlu.doctor.presentation.herb.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.verlu.doctor.data.herb.dto.ArticleMeta
import cn.verlu.doctor.data.local.herb.HerbFavoriteEntity
import cn.verlu.doctor.presentation.herb.HerbCollection
import cn.verlu.doctor.presentation.herb.vm.HerbBrowseViewModel
import cn.verlu.doctor.presentation.herb.vm.HerbFavoritesListViewModel
import cn.verlu.doctor.presentation.herb.vm.HerbHomeViewModel
import cn.verlu.doctor.presentation.herb.vm.HerbSharedFavoritesViewModel
import cn.verlu.doctor.presentation.ui.DoctorPullToRefreshIndicator
import cn.verlu.doctor.presentation.ui.Material3ExpressiveLoadingIndicator
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HerbMainShell(
    modifier: Modifier = Modifier,
    avatarUrl: String? = null,
    onOpenArticle: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenProfile: () -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topAppBarState)
    val favoriteVm: HerbSharedFavoritesViewModel = hiltViewModel()
    val favoritesSnackbarHostState = remember { SnackbarHostState() }

    val titles = listOf("本草", "目录", "收藏")
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(favoritesSnackbarHostState) },
        topBar = {
            LargeTopAppBar(
                title = { Text(titles.getOrElse(tab) { "本草" }) },
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = onOpenProfile) {
                        if (avatarUrl != null) {
                            AsyncImage(
                                model = avatarUrl,
                                contentDescription = "个人信息",
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            Icon(
                                Icons.Default.AccountCircle,
                                contentDescription = "个人信息",
                                modifier = Modifier.size(32.dp),
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onOpenSearch) {
                Icon(Icons.Default.Search, contentDescription = "搜索")
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text("首页") },
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                    label = { Text("目录") },
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    icon = { Icon(Icons.Default.Favorite, contentDescription = null) },
                    label = { Text("收藏") },
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (tab) {
                0 -> HerbHomeTab(
                    modifier = Modifier.fillMaxSize(),
                    favoriteVm = favoriteVm,
                    onOpenArticle = onOpenArticle,
                )
                1 -> HerbBrowseTab(
                    modifier = Modifier.fillMaxSize(),
                    favoriteVm = favoriteVm,
                    onOpenArticle = onOpenArticle,
                )
                2 -> HerbFavoritesTab(
                    modifier = Modifier.fillMaxSize(),
                    snackbarHostState = favoritesSnackbarHostState,
                    onOpenArticle = onOpenArticle,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HerbHomeTab(
    modifier: Modifier = Modifier,
    favoriteVm: HerbSharedFavoritesViewModel,
    onOpenArticle: (String) -> Unit,
) {
    val vm: HerbHomeViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val favoritePaths by favoriteVm.favoritePaths.collectAsStateWithLifecycle()
    val pullToRefreshState = rememberPullToRefreshState()

    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = { vm.refresh() },
        state = pullToRefreshState,
        modifier = modifier.fillMaxSize(),
        indicator = {
            DoctorPullToRefreshIndicator(
                state = pullToRefreshState,
                isRefreshing = state.isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        },
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "Tip：下拉换一篇",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.spotlight?.let { p ->
                item(key = p.path) {
                    HerbSpotlightCard(
                        preview = p,
                        isFavorite = p.path in favoritePaths,
                        onToggleFavorite = {
                            favoriteVm.toggleFavorite(p.path, p.title, p.collection, p.serial)
                        },
                        onOpen = { onOpenArticle(p.path) },
                    )
                }
            }
            if (state.spotlight == null && !state.isRefreshing) {
                item(key = "home_empty") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 280.dp)
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "暂无可用条文，请检查网络后下拉重试",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HerbBrowseTab(
    modifier: Modifier = Modifier,
    favoriteVm: HerbSharedFavoritesViewModel,
    onOpenArticle: (String) -> Unit,
) {
    val vm: HerbBrowseViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val favoritePaths by favoriteVm.favoritePaths.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val pullToRefreshState = rememberPullToRefreshState()

    LaunchedEffect(listState, state.items.size, state.hasMore, state.isAppending, state.isRefreshing) {
        snapshotFlow {
            val li = listState.layoutInfo
            val total = li.totalItemsCount
            if (total <= 0) return@snapshotFlow false
            val last = li.visibleItemsInfo.lastOrNull()?.index ?: -1
            last >= total - 2
        }
            .distinctUntilChanged()
            .collect { nearEnd ->
                if (nearEnd) vm.loadMore()
            }
    }

    Column(modifier) {
        CollectionChipsRow(
            selected = state.collection,
            onSelect = { vm.setCollection(it) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
        state.total?.let { t ->
            Text(
                "共 $t 条（当前卷）",
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { vm.refresh() },
            state = pullToRefreshState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            indicator = {
                DoctorPullToRefreshIndicator(
                    state = pullToRefreshState,
                    isRefreshing = state.isRefreshing,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            },
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.items, key = { it.path }) { m ->
                    if (state.collection == HerbCollection.All) {
                        HerbDirectoryCard(
                            meta = m,
                            isFavorite = m.path in favoritePaths,
                            onToggleFavorite = {
                                favoriteVm.toggleFavorite(m.path, m.title, m.collection, m.serial)
                            },
                            onClick = { onOpenArticle(m.path) },
                        )
                    } else {
                        HerbBrowseCompactRow(
                            meta = m,
                            isFavorite = m.path in favoritePaths,
                            showCollectionColumn = false,
                            onToggleFavorite = {
                                favoriteVm.toggleFavorite(m.path, m.title, m.collection, m.serial)
                            },
                            onClick = { onOpenArticle(m.path) },
                        )
                    }
                }
                if (state.isAppending && state.items.isNotEmpty()) {
                    item(key = "browse_append") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Material3ExpressiveLoadingIndicator()
                        }
                    }
                }
                if (state.items.isEmpty() && !state.isRefreshing) {
                    item(key = "browse_empty_pull") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 360.dp)
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "暂无目录缓存",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "下拉同步；从未同步过当前卷时会自动请求",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HerbFavoritesTab(
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState,
    onOpenArticle: (String) -> Unit,
) {
    val vm: HerbFavoritesListViewModel = hiltViewModel()
    val rows by vm.favorites.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (rows.isEmpty()) {
            item {
                Text(
                    "空空如也",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
        }
        items(rows, key = { it.path }) { row ->
            FavoriteRowCard(
                row = row,
                onOpen = { onOpenArticle(row.path) },
                onRemove = {
                    val removed = row
                    vm.remove(removed.path)
                    scope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = "已从收藏中移除",
                            actionLabel = "撤销",
                            duration = SnackbarDuration.Short,
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            vm.restoreFavorite(removed)
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun FavoriteRowCard(
    row: HerbFavoriteEntity,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
) {
    val meta = ArticleMeta(
        path = row.path,
        collection = row.collection,
        serial = row.serial,
        title = row.title,
        sizeBytes = 0,
        mtime = 0.0,
    )
    HerbBrowseCompactRow(
        meta = meta,
        isFavorite = true,
        showCollectionColumn = false,
        onToggleFavorite = onRemove,
        onClick = onOpen,
    )
}
