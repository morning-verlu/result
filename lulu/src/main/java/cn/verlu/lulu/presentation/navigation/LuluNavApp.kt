package cn.verlu.lulu.presentation.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import cn.verlu.lulu.core.auth.LocalLuluSession
import cn.verlu.lulu.core.design.LuluSpacing
import cn.verlu.lulu.core.feature.FeatureChrome
import cn.verlu.lulu.core.feature.LuluFeatureId
import cn.verlu.lulu.core.feature.LuluFeatureRegistry
import cn.verlu.lulu.presentation.apps.AppsRoute
import cn.verlu.lulu.presentation.auth.ui.AuthEmailRoute
import cn.verlu.lulu.presentation.auth.ui.AuthPasswordRoute
import cn.verlu.lulu.presentation.auth.ui.AuthRoute
import cn.verlu.lulu.presentation.auth.ui.UpdatePasswordDialog
import cn.verlu.lulu.presentation.auth.vm.AuthEventManager
import cn.verlu.lulu.presentation.auth.vm.AuthFormViewModel
import cn.verlu.lulu.presentation.chat.ChatRoute
import cn.verlu.lulu.presentation.cnchess.CnChessRoute
import cn.verlu.lulu.presentation.cloud.CloudRoute
import cn.verlu.lulu.presentation.doctor.DoctorRoute
import cn.verlu.lulu.domain.sync.SyncStatusType
import cn.verlu.lulu.presentation.lifestream.LifeStreamAppRoute
import cn.verlu.lulu.presentation.memory.MemoryCreateRoute
import cn.verlu.lulu.presentation.memory.MemoryDetailRoute
import cn.verlu.lulu.presentation.memory.MemoryRoute
import cn.verlu.lulu.presentation.memory.MemorySearchRoute
import cn.verlu.lulu.presentation.mine.MineRoute
import cn.verlu.lulu.presentation.musicapp.MusicAppRoute
import cn.verlu.lulu.presentation.session.SessionViewModel
import cn.verlu.lulu.presentation.syncapp.SyncAppRoute
import cn.verlu.lulu.presentation.sync.SyncStatusDetailRoute
import cn.verlu.lulu.presentation.sync.title
import cn.verlu.lulu.presentation.talk.TalkRoute
import cn.verlu.lulu.presentation.today.TodayRoute
import cn.verlu.lulu.presentation.update.LuluAppUpdateGate
import kotlinx.serialization.Serializable

val LocalSnackbarHostState = compositionLocalOf<SnackbarHostState> {
    error("No SnackbarHostState provided")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LuluNavApp(
    modifier: Modifier = Modifier,
    sessionViewModel: SessionViewModel = hiltViewModel(),
    authFormViewModel: AuthFormViewModel = hiltViewModel(),
) {
    val session by sessionViewModel.state.collectAsStateWithLifecycle()
    val backStack = rememberNavBackStack(if (session.isAuthenticated) AppRoute.Today else AppRoute.Auth)
    val snackbarHostState = remember { SnackbarHostState() }
    val showUpdatePasswordDialog by AuthEventManager.showPasswordResetDialog.collectAsStateWithLifecycle()
    val pendingSyncSso by AuthEventManager.pendingSsoAuthorize.collectAsStateWithLifecycle()
    var topBarActions by remember {
        mutableStateOf<Map<AppRoute, @Composable RowScope.() -> Unit>>(emptyMap())
    }

    LaunchedEffect(session.isAuthenticated) {
        val target = if (session.isAuthenticated) AppRoute.Today else AppRoute.Auth
        if (backStack.lastOrNull() != target) {
            while (backStack.isNotEmpty()) backStack.removeLastOrNull()
            backStack.add(target)
        }
    }

    LaunchedEffect(pendingSyncSso) {
        if (pendingSyncSso != null && backStack.lastOrNull() != AppRoute.SyncApp) {
            backStack.add(AppRoute.SyncApp)
        }
    }

    val top = backStack.lastOrNull() ?: AppRoute.Today
    val chrome = top.routeChrome()
    val showAuthTopBar = top == AppRoute.AuthEmail || top == AppRoute.AuthPassword
    val showTopBar = showAuthTopBar || (session.isAuthenticated && chrome == FeatureChrome.LuluShell)
    val showBottomBar = session.isAuthenticated && chrome == FeatureChrome.LuluShell && top in rootRoutes
    val canPop = backStack.size > 1 && top !in rootRoutes
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    CompositionLocalProvider(
        LocalSnackbarHostState provides snackbarHostState,
        LocalLuluSession provides session,
    ) {
        Scaffold(
            modifier = if (chrome == FeatureChrome.LuluShell || showAuthTopBar) {
                modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
            } else {
                modifier
            },
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                if (showTopBar) {
                    LargeTopAppBar(
                        title = { Text(titleFor(top)) },
                        scrollBehavior = scrollBehavior,
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background,
                            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        ),
                        navigationIcon = {
                            if (canPop) {
                                IconButton(onClick = { backStack.removeLastOrNull() }) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "返回",
                                    )
                                }
                            }
                        },
                        actions = {
                            topBarActions[top as? AppRoute]?.invoke(this)
                        },
                    )
                }
            },
            floatingActionButton = {
                if (session.isAuthenticated && top == AppRoute.Memory) {
                    FloatingActionButton(onClick = { backStack.add(AppRoute.MemoryCreate) }) {
                        Icon(Icons.Default.Add, contentDescription = "新建记忆")
                    }
                }
            },
            bottomBar = {
                if (showBottomBar) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        NavigationBar(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = LuluSpacing.bottomNavMaxWidth),
                        ) {
                            NavigationBarItem(
                                selected = top == AppRoute.Today,
                                onClick = { switchRoot(backStack, AppRoute.Today) },
                                icon = { Icon(Icons.Default.Home, contentDescription = "今天") },
                                label = { Text("今天") },
                            )
                            NavigationBarItem(
                                selected = top == AppRoute.Memory,
                                onClick = { switchRoot(backStack, AppRoute.Memory) },
                                icon = { Icon(Icons.Default.Favorite, contentDescription = "记忆") },
                                label = { Text("记忆") },
                            )
                            NavigationBarItem(
                                selected = top == AppRoute.Apps,
                                onClick = { switchRoot(backStack, AppRoute.Apps) },
                                icon = { Icon(Icons.Default.Apps, contentDescription = "应用") },
                                label = { Text("应用") },
                            )
                            NavigationBarItem(
                                selected = top == AppRoute.Mine,
                                onClick = { switchRoot(backStack, AppRoute.Mine) },
                                icon = { Icon(Icons.Default.Person, contentDescription = "我的") },
                                label = { Text("我的") },
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            if (showUpdatePasswordDialog) {
                UpdatePasswordDialog(
                    onDismiss = { AuthEventManager.showPasswordResetDialog.value = false },
                )
            }
            LuluAppUpdateGate(
                showMessage = { snackbarHostState.showSnackbar(it) },
            )

            NavDisplay(
                backStack = backStack,
                onBack = { backStack.removeLastOrNull() },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                transitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
                popTransitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
                predictivePopTransitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
                entryProvider = entryProvider {
                    entry<AppRoute.Auth> {
                        AuthRoute(
                            viewModel = authFormViewModel,
                            onOpenEmailLogin = { backStack.add(AppRoute.AuthEmail) },
                            onOpenEmailRegister = { backStack.add(AppRoute.AuthEmail) },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    entry<AppRoute.AuthEmail> {
                        AuthEmailRoute(
                            viewModel = authFormViewModel,
                            onNext = { backStack.add(AppRoute.AuthPassword) },
                            setTopBarActions = { action ->
                                topBarActions =
                                    if (action == null) {
                                        topBarActions - AppRoute.AuthEmail
                                    } else {
                                        topBarActions + (AppRoute.AuthEmail to action)
                                    }
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    entry<AppRoute.AuthPassword> {
                        AuthPasswordRoute(
                            viewModel = authFormViewModel,
                            onDone = { },
                            setTopBarActions = { action ->
                                topBarActions =
                                    if (action == null) {
                                        topBarActions - AppRoute.AuthPassword
                                    } else {
                                        topBarActions + (AppRoute.AuthPassword to action)
                                    }
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    entry<AppRoute.Today> {
                        TodayRoute(
                            isOfflineSession = session.isOfflineSession,
                            onMemoryClick = { memoryId -> backStack.add(AppRoute.MemoryDetail(memoryId)) },
                            onStatusClick = { type -> backStack.add(AppRoute.SyncStatusDetail(type)) },
                            onOpenSyncApp = { backStack.add(AppRoute.SyncApp) },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    entry<AppRoute.SyncStatusDetail> { route ->
                        SyncStatusDetailRoute(
                            type = route.type,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    entry<AppRoute.Memory> {
                        MemoryRoute(
                            onMemoryClick = { memoryId -> backStack.add(AppRoute.MemoryDetail(memoryId)) },
                            onOpenSearch = { backStack.add(AppRoute.MemorySearch) },
                            setTopBarActions = { action ->
                                topBarActions =
                                    if (action == null) {
                                        topBarActions - AppRoute.Memory
                                    } else {
                                        topBarActions + (AppRoute.Memory to action)
                                    }
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    entry<AppRoute.MemorySearch> {
                        MemorySearchRoute(
                            onMemoryClick = { memoryId -> backStack.add(AppRoute.MemoryDetail(memoryId)) },
                            onBack = { backStack.removeLastOrNull() },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    entry<AppRoute.MemoryCreate> {
                        MemoryCreateRoute(
                            onBack = { backStack.removeLastOrNull() },
                            onSaved = { returnAfterMemorySave(backStack) },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    entry<AppRoute.MemoryDetail> { route ->
                        MemoryDetailRoute(
                            memoryId = route.memoryId,
                            onBack = { backStack.removeLastOrNull() },
                            onDeleted = { returnAfterMemoryDelete(backStack) },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    entry<AppRoute.Mine> {
                        MineRoute(
                            user = session.user,
                            isOfflineSession = session.isOfflineSession,
                            onSignOut = sessionViewModel::signOut,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    entry<AppRoute.Apps> {
                        AppsRoute(
                            onOpenFeature = { featureId -> backStack.add(routeForFeature(featureId)) },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    entry<AppRoute.SyncApp> {
                        SyncAppRoute(
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    entry<AppRoute.LifeStream> {
                        LifeStreamAppRoute(
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    entry<AppRoute.Cloud> {
                        CloudRoute(
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    entry<AppRoute.Music> {
                        MusicAppRoute(
                            modifier = Modifier.fillMaxSize(),
                            onExit = { backStack.removeLastOrNull() },
                        )
                    }
                    entry<AppRoute.Chat> {
                        ChatRoute(
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    entry<AppRoute.Talk> {
                        TalkRoute(
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    entry<AppRoute.Doctor> {
                        DoctorRoute(
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    entry<AppRoute.CnChess> {
                        CnChessRoute(
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                },
            )
        }
    }
}

private fun switchRoot(
    backStack: MutableList<NavKey>,
    route: AppRoute,
) {
    if (backStack.lastOrNull() == route) return
    while (backStack.isNotEmpty()) backStack.removeLastOrNull()
    backStack.add(route)
}

private fun returnAfterMemorySave(backStack: MutableList<NavKey>) {
    val previous = backStack.getOrNull(backStack.lastIndex - 1)
    switchRoot(
        backStack = backStack,
        route = if (previous == AppRoute.Today) AppRoute.Today else AppRoute.Memory,
    )
}

private fun returnAfterMemoryDelete(backStack: MutableList<NavKey>) {
    val previous = backStack.getOrNull(backStack.lastIndex - 1)
    switchRoot(
        backStack = backStack,
        route = if (previous == AppRoute.Today) AppRoute.Today else AppRoute.Memory,
    )
}

private fun titleFor(route: NavKey): String = when (route) {
    AppRoute.Auth -> "登录"
    AppRoute.AuthEmail -> "邮箱"
    AppRoute.AuthPassword -> "密码"
    AppRoute.Today -> "今天"
    is AppRoute.SyncStatusDetail -> route.type.title()
    AppRoute.Memory -> "记忆"
    AppRoute.MemorySearch -> "搜索记忆"
    AppRoute.MemoryCreate -> "新建记忆"
    is AppRoute.MemoryDetail -> "记忆详情"
    AppRoute.LifeStream -> "记忆"
    AppRoute.Apps -> "应用"
    AppRoute.Mine -> "我的"
    else -> featureIdForRoute(route)?.let { LuluFeatureRegistry.require(it).title } ?: "Lulu"
}

private val rootRoutes: Set<NavKey> = setOf(AppRoute.Today, AppRoute.Memory, AppRoute.Apps, AppRoute.Mine)

private fun NavKey.routeChrome(): FeatureChrome = when (this) {
    AppRoute.Auth,
    AppRoute.MemoryCreate,
    AppRoute.MemorySearch,
    is AppRoute.MemoryDetail,
    -> FeatureChrome.Editor

    AppRoute.LifeStream -> FeatureChrome.FullscreenApp

    else -> featureIdForRoute(this)
        ?.let { LuluFeatureRegistry.require(it).chrome }
        ?: FeatureChrome.LuluShell
}

private fun routeForFeature(featureId: LuluFeatureId): AppRoute = when (featureId) {
    LuluFeatureId.Sync -> AppRoute.SyncApp
    LuluFeatureId.LifeStream -> AppRoute.LifeStream
    LuluFeatureId.Talk -> AppRoute.Talk
    LuluFeatureId.Music -> AppRoute.Music
    LuluFeatureId.Doctor -> AppRoute.Doctor
    LuluFeatureId.CnChess -> AppRoute.CnChess
    LuluFeatureId.CloudDrive -> AppRoute.Cloud
    LuluFeatureId.LuluChat -> AppRoute.Chat
}

private fun featureIdForRoute(route: NavKey): LuluFeatureId? = when (route) {
    AppRoute.SyncApp -> LuluFeatureId.Sync
    AppRoute.LifeStream -> LuluFeatureId.LifeStream
    AppRoute.Talk -> LuluFeatureId.Talk
    AppRoute.Music -> LuluFeatureId.Music
    AppRoute.Doctor -> LuluFeatureId.Doctor
    AppRoute.CnChess -> LuluFeatureId.CnChess
    AppRoute.Cloud -> LuluFeatureId.CloudDrive
    AppRoute.Chat -> LuluFeatureId.LuluChat
    else -> null
}

sealed interface AppRoute : NavKey {
    @Serializable
    data object Auth : AppRoute

    @Serializable
    data object AuthEmail : AppRoute

    @Serializable
    data object AuthPassword : AppRoute

    @Serializable
    data object Today : AppRoute

    @Serializable
    data class SyncStatusDetail(val type: SyncStatusType) : AppRoute

    @Serializable
    data object Memory : AppRoute

    @Serializable
    data object MemoryCreate : AppRoute

    @Serializable
    data object MemorySearch : AppRoute

    @Serializable
    data class MemoryDetail(val memoryId: String) : AppRoute

    @Serializable
    data object Mine : AppRoute

    @Serializable
    data object Apps : AppRoute

    @Serializable
    data object Cloud : AppRoute

    @Serializable
    data object Music : AppRoute

    @Serializable
    data object Chat : AppRoute

    @Serializable
    data object Talk : AppRoute

    @Serializable
    data object Doctor : AppRoute

    @Serializable
    data object CnChess : AppRoute

    @Serializable
    data object SyncApp : AppRoute

    @Serializable
    data object LifeStream : AppRoute
}
