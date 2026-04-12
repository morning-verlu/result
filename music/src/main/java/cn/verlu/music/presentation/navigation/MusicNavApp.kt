package cn.verlu.music.presentation.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.DrawerState
import androidx.compose.material3.rememberDrawerState
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import cn.verlu.music.presentation.music.ui.DiscoverRoute
import cn.verlu.music.presentation.music.ui.FavoritesRoute
import cn.verlu.music.presentation.music.ui.HiddenTracksRoute
import cn.verlu.music.presentation.music.ui.LocalMusicRoute
import cn.verlu.music.presentation.music.vm.MusicDrawerViewModel
import cn.verlu.music.presentation.music.ui.NowPlayingRoute
import cn.verlu.music.presentation.music.ui.OnlineMusicRoute
import cn.verlu.music.presentation.music.ui.DownloadManagerRoute
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

sealed interface MusicRoute : NavKey {
    @Serializable
    data object Discover : MusicRoute

    @Serializable
    data object Favorites : MusicRoute

    @Serializable
    data object Local : MusicRoute

    @Serializable
    data object NowPlaying : MusicRoute

    @Serializable
    data object HiddenTracks : MusicRoute

    @Serializable
    data object OnlineCatalog : MusicRoute

    @Serializable
    data object DownloadManager : MusicRoute
}

/** 与 Material 指导一致：约 600dp 及以上用 NavigationRail */
private fun useNavigationRail(screenWidthDp: Int): Boolean = screenWidthDp >= 600

/** 返回键关抽屉比默认 [DrawerState.close] 略慢、曲线更缓，更接近手指滑开释手，减轻过快关合与底层页面重绘叠加的闪烁感。 */
private val drawerCloseOnBackSpec =
    tween<Float>(durationMillis = 320, easing = FastOutSlowInEasing)

/** Material3 的 [DrawerState.close] 无法指定时长；在仍提供 [DrawerState.animateTo] 的版本上用自定义 spec（API 已标弃用但暂无等价替换）。 */
@Suppress("DEPRECATION")
private suspend fun DrawerState.closeAnimated(spec: AnimationSpec<Float>) {
    animateTo(DrawerValue.Closed, spec)
}

@Composable
fun MusicNavApp(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val rail = useNavigationRail(configuration.screenWidthDp)
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val drawerViewModel: MusicDrawerViewModel = hiltViewModel()

    val backStack = rememberNavBackStack(MusicRoute.Discover)
    val pop: () -> Unit = {
        if (backStack.size <= 1) {
            (context as? ComponentActivity)?.finish()
        } else {
            backStack.removeLastOrNull()
        }
    }
    val currentKey = backStack.lastOrNull() ?: MusicRoute.Discover

    fun replaceRoot(route: MusicRoute) {
        while (backStack.isNotEmpty()) backStack.removeLastOrNull()
        backStack.add(route)
    }

    val showMainNav =
        currentKey == MusicRoute.Discover ||
            currentKey == MusicRoute.Favorites ||
            currentKey == MusicRoute.Local

    val openDrawer: () -> Unit = {
        scope.launch { drawerState.open() }
    }

    @Composable
    fun MainNavItems() {
        NavigationRailItem(
            selected = currentKey == MusicRoute.Local,
            onClick = {
                if (currentKey != MusicRoute.Local) replaceRoot(MusicRoute.Local)
            },
            icon = { Icon(Icons.Default.LibraryMusic, contentDescription = null) },
            label = { Text("本地") }
        )
        NavigationRailItem(
            selected = currentKey == MusicRoute.Discover,
            onClick = {
                if (currentKey != MusicRoute.Discover) replaceRoot(MusicRoute.Discover)
            },
            icon = { Icon(Icons.Default.Shuffle, contentDescription = null) },
            label = { Text("发现") }
        )
        NavigationRailItem(
            selected = currentKey == MusicRoute.Favorites,
            onClick = {
                if (currentKey != MusicRoute.Favorites) replaceRoot(MusicRoute.Favorites)
            },
            icon = { Icon(Icons.Default.Favorite, contentDescription = null) },
            label = { Text("收藏") }
        )
    }

    @Composable
    fun BottomNavBar() {
        NavigationBar {
            NavigationBarItem(
                selected = currentKey == MusicRoute.Local,
                onClick = {
                    if (currentKey != MusicRoute.Local) replaceRoot(MusicRoute.Local)
                },
                icon = { Icon(Icons.Default.LibraryMusic, contentDescription = null) },
                label = { Text("本地") }
            )
            NavigationBarItem(
                selected = currentKey == MusicRoute.Discover,
                onClick = {
                    if (currentKey != MusicRoute.Discover) replaceRoot(MusicRoute.Discover)
                },
                icon = { Icon(Icons.Default.Shuffle, contentDescription = null) },
                label = { Text("发现") }
            )
            NavigationBarItem(
                selected = currentKey == MusicRoute.Favorites,
                onClick = {
                    if (currentKey != MusicRoute.Favorites) replaceRoot(MusicRoute.Favorites)
                },
                icon = { Icon(Icons.Default.Favorite, contentDescription = null) },
                label = { Text("收藏") }
            )
        }
    }

    @Composable
    fun NavContent(contentPadding: PaddingValues) {
        NavDisplay(
            backStack = backStack,
            onBack = pop,
            modifier = modifier.fillMaxSize().padding(contentPadding),
            transitionSpec = {
                fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(220))
            },
            popTransitionSpec = {
                fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(220))
            },
            predictivePopTransitionSpec = {
                fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(220))
            },
            entryProvider = entryProvider {
                entry<MusicRoute.Discover> {
                    DiscoverRoute(
                        modifier = Modifier.fillMaxSize(),
                        viewModel = hiltViewModel(),
                        onNavigateToNowPlaying = { backStack.add(MusicRoute.NowPlaying) },
                        onNavigateToOnlineCatalog = { backStack.add(MusicRoute.OnlineCatalog) },
                        onNavigateToDownloadManager = { backStack.add(MusicRoute.DownloadManager) }
                    )
                }
                entry<MusicRoute.Favorites> {
                    FavoritesRoute(
                        modifier = Modifier.fillMaxSize(),
                        viewModel = hiltViewModel(),
                        onNavigateToNowPlaying = { backStack.add(MusicRoute.NowPlaying) }
                    )
                }
                entry<MusicRoute.Local> {
                    LocalMusicRoute(
                        modifier = Modifier.fillMaxSize(),
                        viewModel = hiltViewModel(),
                        onNavigateToNowPlaying = { backStack.add(MusicRoute.NowPlaying) },
                        onNavigateToHiddenTracks = { backStack.add(MusicRoute.HiddenTracks) }
                    )
                }
                entry<MusicRoute.NowPlaying> {
                    NowPlayingRoute(
                        modifier = Modifier.fillMaxSize(),
                        viewModel = hiltViewModel(),
                        onNavigateUp = pop
                    )
                }
                entry<MusicRoute.HiddenTracks> {
                    HiddenTracksRoute(
                        modifier = Modifier.fillMaxSize(),
                        viewModel = hiltViewModel(),
                        onNavigateUp = pop
                    )
                }
                entry<MusicRoute.OnlineCatalog> {
                    OnlineMusicRoute(
                        modifier = Modifier.fillMaxSize(),
                        viewModel = hiltViewModel(),
                        onNavigateUp = pop,
                        onNavigateToNowPlaying = { backStack.add(MusicRoute.NowPlaying) }
                    )
                }
                entry<MusicRoute.DownloadManager> {
                    DownloadManagerRoute(
                        modifier = Modifier.fillMaxSize(),
                        onNavigateUp = pop
                    )
                }
            }
        )
    }

    CompositionLocalProvider(LocalOpenMusicDrawer provides openDrawer) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = showMainNav,
            drawerContent = {
                MusicDrawerSheetContent(
                    viewModel = drawerViewModel,
                    onNavigateDownloadManager = {
                        scope.launch { drawerState.close() }
                        backStack.add(MusicRoute.DownloadManager)
                    }
                )
            }
        ) {
            BackHandler(enabled = drawerState.isOpen) {
                scope.launch { drawerState.closeAnimated(drawerCloseOnBackSpec) }
            }
            if (rail && showMainNav) {
                Row(Modifier.fillMaxSize()) {
                    NavigationRail(modifier = Modifier.fillMaxHeight()) {
                        MainNavItems()
                    }
                    Box(Modifier.weight(1f)) {
                        NavContent(PaddingValues(0.dp))
                    }
                }
            } else {
                Scaffold(
                    contentWindowInsets = WindowInsets(0.dp),
                    bottomBar = {
                        if (showMainNav && !rail) {
                            BottomNavBar()
                        }
                    }
                ) { innerPadding ->
                    NavContent(innerPadding)
                }
            }
        }
    }
}
