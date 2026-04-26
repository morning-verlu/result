package cn.verlu.memory.presentation.navigation

import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.RowScope
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import cn.verlu.memory.presentation.auth.ui.AuthEmailRoute
import cn.verlu.memory.presentation.auth.ui.AuthPasswordRoute
import cn.verlu.memory.presentation.auth.ui.AuthRoute
import cn.verlu.memory.presentation.auth.ui.UpdatePasswordDialog
import cn.verlu.memory.presentation.auth.vm.AuthEventManager
import cn.verlu.memory.presentation.auth.vm.AuthSessionViewModel
import cn.verlu.memory.presentation.lifestream.ui.LifeStreamRoute
import cn.verlu.memory.presentation.lifestream.ui.LifeStreamScreen
import cn.verlu.memory.presentation.update.AppUpdateGate
import androidx.compose.ui.unit.dp
import kotlinx.serialization.Serializable

private fun isAuthSubFlow(route: NavKey?): Boolean {
    val r = route as? MemoryRoute ?: return false
    return r == MemoryRoute.Auth || r == MemoryRoute.AuthEmail || r == MemoryRoute.AuthPassword
}

@Serializable
private sealed interface MemoryRoute : NavKey {
    @Serializable
    data object Auth : MemoryRoute

    @Serializable
    data object AuthEmail : MemoryRoute

    @Serializable
    data object AuthPassword : MemoryRoute

    @Serializable
    data object Home : MemoryRoute

    @Serializable
    data object Search : MemoryRoute

    @Serializable
    data object Profile : MemoryRoute

    @Serializable
    data object Record : MemoryRoute

    @Serializable
    data class Detail(val entryId: String) : MemoryRoute

    @Serializable
    data object Settings : MemoryRoute
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryNavApp(
    modifier: Modifier = Modifier,
) {
    val backStack = rememberNavBackStack(MemoryRoute.Home)
    val pop: () -> Unit = { backStack.removeLastOrNull() }
    val authSessionVm: AuthSessionViewModel = hiltViewModel()
    val authState by authSessionVm.state.collectAsStateWithLifecycle()
    val showUpdatePasswordDialog by AuthEventManager.showPasswordResetDialog.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var prevAuthenticated by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(authState.isInitializing, authState.isAuthenticated) {
        if (authState.isInitializing) return@LaunchedEffect

        val wasAuthenticated = prevAuthenticated
        prevAuthenticated = authState.isAuthenticated

        if (authState.isAuthenticated) {
            val justLoggedIn = wasAuthenticated == false
            val current = backStack.lastOrNull()
            if (justLoggedIn && isAuthSubFlow(current)) {
                while (backStack.isNotEmpty()) backStack.removeLastOrNull()
                backStack.add(MemoryRoute.Home)
            }
            return@LaunchedEffect
        }

        val current = backStack.lastOrNull()
        if (isAuthSubFlow(current)) return@LaunchedEffect
        while (backStack.isNotEmpty()) backStack.removeLastOrNull()
        backStack.add(MemoryRoute.Auth)
    }

    CompositionLocalProvider(LocalSnackbarHostState provides snackbarHostState) {
        Scaffold(
            modifier = modifier,
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0.dp),
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { snackbarPadding ->
            if (showUpdatePasswordDialog) {
                UpdatePasswordDialog(
                    onDismiss = { AuthEventManager.showPasswordResetDialog.value = false },
                )
            }
            AppUpdateGate(
                showMessage = { snackbarHostState.showSnackbar(it) },
            )
            NavDisplay(
                backStack = backStack,
                onBack = pop,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(snackbarPadding),
                transitionSpec = {
                    slideInHorizontally(initialOffsetX = { it }) togetherWith
                        slideOutHorizontally(targetOffsetX = { -it })
                },
                popTransitionSpec = {
                    slideInHorizontally(initialOffsetX = { -it }) togetherWith
                        slideOutHorizontally(targetOffsetX = { it })
                },
                predictivePopTransitionSpec = {
                    slideInHorizontally(initialOffsetX = { -it }) togetherWith
                        slideOutHorizontally(targetOffsetX = { it })
                },
                entryProvider = entryProvider {
                    entry<MemoryRoute.Auth> {
                        AuthLandingWithShell(
                            modifier = Modifier.fillMaxSize(),
                            onOpenEmailLogin = { backStack.add(MemoryRoute.AuthEmail) },
                            onOpenEmailRegister = { backStack.add(MemoryRoute.AuthEmail) },
                        )
                    }
                    entry<MemoryRoute.AuthEmail> {
                        AuthEmailWithShell(
                            modifier = Modifier.fillMaxSize(),
                            onBack = pop,
                            onNext = { backStack.add(MemoryRoute.AuthPassword) },
                        )
                    }
                    entry<MemoryRoute.AuthPassword> {
                        AuthPasswordWithShell(
                            modifier = Modifier.fillMaxSize(),
                            onBack = pop,
                        )
                    }
                    entry<MemoryRoute.Home> {
                        HomeRouteWithShell(
                            route = LifeStreamRoute.Home,
                            onNavigate = { next ->
                                when (next) {
                                    LifeStreamRoute.Home -> Unit
                                    LifeStreamRoute.Search -> backStack.add(MemoryRoute.Search)
                                    LifeStreamRoute.Profile -> backStack.add(MemoryRoute.Profile)
                                    LifeStreamRoute.Record -> backStack.add(MemoryRoute.Record)
                                    is LifeStreamRoute.Detail -> backStack.add(MemoryRoute.Detail(next.entryId))
                                    LifeStreamRoute.Settings -> backStack.add(MemoryRoute.Settings)
                                }
                            },
                            onBack = pop,
                        )
                    }
                    entry<MemoryRoute.Search> {
                        HomeRouteWithShell(
                            route = LifeStreamRoute.Search,
                            onNavigate = { next ->
                                when (next) {
                                    LifeStreamRoute.Home -> Unit
                                    LifeStreamRoute.Search -> Unit
                                    LifeStreamRoute.Profile -> backStack.add(MemoryRoute.Profile)
                                    LifeStreamRoute.Record -> backStack.add(MemoryRoute.Record)
                                    is LifeStreamRoute.Detail -> backStack.add(MemoryRoute.Detail(next.entryId))
                                    LifeStreamRoute.Settings -> backStack.add(MemoryRoute.Settings)
                                }
                            },
                            onBack = pop,
                        )
                    }
                    entry<MemoryRoute.Profile> {
                        HomeRouteWithShell(
                            route = LifeStreamRoute.Profile,
                            onNavigate = { next ->
                                when (next) {
                                    LifeStreamRoute.Home -> Unit
                                    LifeStreamRoute.Search -> backStack.add(MemoryRoute.Search)
                                    LifeStreamRoute.Profile -> Unit
                                    LifeStreamRoute.Record -> backStack.add(MemoryRoute.Record)
                                    is LifeStreamRoute.Detail -> backStack.add(MemoryRoute.Detail(next.entryId))
                                    LifeStreamRoute.Settings -> backStack.add(MemoryRoute.Settings)
                                }
                            },
                            onBack = pop,
                        )
                    }
                    entry<MemoryRoute.Record> {
                        HomeRouteWithShell(
                            route = LifeStreamRoute.Record,
                            onNavigate = { next ->
                                if (next is LifeStreamRoute.Detail) {
                                    backStack.add(MemoryRoute.Detail(next.entryId))
                                }
                            },
                            onBack = pop,
                        )
                    }
                    entry<MemoryRoute.Detail> { route ->
                        HomeRouteWithShell(
                            route = LifeStreamRoute.Detail(route.entryId),
                            onNavigate = { next ->
                                when (next) {
                                    LifeStreamRoute.Record -> backStack.add(MemoryRoute.Record)
                                    is LifeStreamRoute.Detail -> backStack.add(MemoryRoute.Detail(next.entryId))
                                    LifeStreamRoute.Search -> backStack.add(MemoryRoute.Search)
                                    LifeStreamRoute.Profile -> backStack.add(MemoryRoute.Profile)
                                    LifeStreamRoute.Settings -> backStack.add(MemoryRoute.Settings)
                                    LifeStreamRoute.Home -> Unit
                                }
                            },
                            onBack = pop,
                        )
                    }
                    entry<MemoryRoute.Settings> {
                        HomeRouteWithShell(
                            route = LifeStreamRoute.Settings,
                            onNavigate = {},
                            onBack = pop,
                        )
                    }
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthLandingWithShell(
    modifier: Modifier = Modifier,
    onOpenEmailLogin: () -> Unit,
    onOpenEmailRegister: () -> Unit,
) {
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topAppBarState)
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            LargeTopAppBar(
                title = { Text("登录") },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        AuthRoute(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            onOpenEmailLogin = onOpenEmailLogin,
            onOpenEmailRegister = onOpenEmailRegister,
        )
    }
}

@Composable
private fun HomeRouteWithShell(
    route: LifeStreamRoute,
    onNavigate: (LifeStreamRoute) -> Unit,
    onBack: () -> Unit,
) {
    LifeStreamScreen(
        route = route,
        onNavigate = onNavigate,
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthEmailWithShell(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    var topBarActions by remember { mutableStateOf<(@Composable RowScope.() -> Unit)?>(null) }
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = { Text("邮箱") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = { topBarActions?.invoke(this) },
            )
        },
    ) { innerPadding ->
        AuthEmailRoute(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            onNext = onNext,
            setTopBarActions = { topBarActions = it },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthPasswordWithShell(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
) {
    var topBarActions by remember { mutableStateOf<(@Composable RowScope.() -> Unit)?>(null) }
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = { Text("密码") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = { topBarActions?.invoke(this) },
            )
        },
    ) { innerPadding ->
        AuthPasswordRoute(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            onDone = {},
            setTopBarActions = { topBarActions = it },
        )
    }
}
