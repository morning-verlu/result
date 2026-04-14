package cn.verlu.cnchess.presentation.navigation

import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import cn.verlu.cnchess.presentation.auth.ui.AuthEmailRoute
import cn.verlu.cnchess.presentation.auth.ui.AuthPasswordRoute
import cn.verlu.cnchess.presentation.auth.ui.AuthRoute
import cn.verlu.cnchess.presentation.auth.ui.UpdatePasswordDialog
import cn.verlu.cnchess.presentation.auth.vm.AuthEventManager
import cn.verlu.cnchess.presentation.auth.vm.AuthSessionViewModel
import cn.verlu.cnchess.presentation.friends.FriendsScreen
import cn.verlu.cnchess.presentation.game.GameScreen
import cn.verlu.cnchess.presentation.history.GameHistoryScreen
import cn.verlu.cnchess.presentation.invite.IncomingInviteDialog
import cn.verlu.cnchess.presentation.invite.InviteListenerViewModel
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable

private fun isAuthSubFlow(route: NavKey?): Boolean {
    val r = route as? AppRoute ?: return false
    return r == AppRoute.Auth || r == AppRoute.AuthEmail || r == AppRoute.AuthPassword
}

val LocalSnackbarHostState = compositionLocalOf<SnackbarHostState> {
    error("No SnackbarHostState provided")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CnChessNavApp(modifier: Modifier = Modifier) {
    val backStack = rememberNavBackStack(AppRoute.Home)
    val pop: () -> Unit = { backStack.removeLastOrNull() }

    val authSessionVm: AuthSessionViewModel = hiltViewModel()
    val inviteVm: InviteListenerViewModel = hiltViewModel()
    val authState by authSessionVm.state.collectAsStateWithLifecycle()
    val inviteState by inviteVm.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val showPasswordResetDialog by AuthEventManager.showPasswordResetDialog.collectAsStateWithLifecycle()
    var prevAuthenticated by remember { mutableStateOf<Boolean?>(null) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, authState.isAuthenticated) {
        val observer = LifecycleEventObserver { _, event ->
            if (!authState.isAuthenticated) return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_START -> inviteVm.setForeground(true)
                Lifecycle.Event.ON_STOP -> inviteVm.setForeground(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(authState.isAuthenticated) {
        if (!authState.isAuthenticated) return@LaunchedEffect
        while (true) {
            inviteVm.heartbeat()
            delay(20_000)
        }
    }

    LaunchedEffect(Unit) {
        inviteVm.navigateToGame.collect { gameId ->
            val current = backStack.lastOrNull()
            if (current != AppRoute.Game(gameId = gameId, startInReplayMode = false)) {
                backStack.add(AppRoute.Game(gameId = gameId, startInReplayMode = false))
            }
        }
    }

    LaunchedEffect(inviteState.error) {
        val err = inviteState.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(err)
        inviteVm.clearError()
    }

    LaunchedEffect(authState.isInitializing, authState.isAuthenticated) {
        if (authState.isInitializing) return@LaunchedEffect
        val wasAuthenticated = prevAuthenticated
        prevAuthenticated = authState.isAuthenticated

        if (authState.isAuthenticated) {
            val justLoggedIn = wasAuthenticated == false
            val current = backStack.lastOrNull()
            if (justLoggedIn && isAuthSubFlow(current)) {
                while (backStack.isNotEmpty()) backStack.removeLastOrNull()
                backStack.add(AppRoute.Home)
                snackbarHostState.showSnackbar("授权登录成功")
            }
            return@LaunchedEffect
        }

        val current = backStack.lastOrNull()
        if (isAuthSubFlow(current)) return@LaunchedEffect
        while (backStack.isNotEmpty()) backStack.removeLastOrNull()
        backStack.add(AppRoute.Auth)
    }

    CompositionLocalProvider(LocalSnackbarHostState provides snackbarHostState) {
        Scaffold(
            modifier = modifier,
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0),
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { snackbarPadding ->
            if (showPasswordResetDialog) {
                UpdatePasswordDialog(
                    onDismiss = { AuthEventManager.showPasswordResetDialog.value = false },
                )
            }
            inviteState.incomingInvite?.let { invite ->
                IncomingInviteDialog(
                    invite = invite,
                    onAccept = { inviteVm.acceptInvite(invite.id) },
                    onReject = { inviteVm.rejectInvite(invite.id) },
                )
            }
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
                    entry<AppRoute.Auth> {
                        AuthLandingWithShell(
                            modifier = Modifier.fillMaxSize(),
                            onOpenEmailLogin = { backStack.add(AppRoute.AuthEmail) },
                            onOpenEmailRegister = { backStack.add(AppRoute.AuthEmail) },
                        )
                    }
                    entry<AppRoute.AuthEmail> {
                        AuthEmailWithShell(
                            modifier = Modifier.fillMaxSize(),
                            onBack = pop,
                            onNext = { backStack.add(AppRoute.AuthPassword) },
                        )
                    }
                    entry<AppRoute.AuthPassword> {
                        AuthPasswordWithShell(
                            modifier = Modifier.fillMaxSize(),
                            onBack = pop,
                        )
                    }
                    entry<AppRoute.Home> {
                        val avatarUrl = authState.user?.userMetadata
                            ?.get("avatar_url")?.toString()
                            ?.trim('"')
                            ?.takeIf { it.isNotBlank() && it != "null" }
                        HomeRouteWithShell(
                            modifier = Modifier.fillMaxSize(),
                            avatarUrl = avatarUrl,
                            onOpenHistory = { backStack.add(AppRoute.GameHistory) },
                            onOpenProfile = { backStack.add(AppRoute.Profile) },
                        )
                    }
                    entry<AppRoute.Profile> {
                        ProfileRouteWithShell(
                            modifier = Modifier.fillMaxSize(),
                            onBack = pop,
                        )
                    }
                    entry<AppRoute.GameHistory> {
                        GameHistoryRouteWithShell(
                            modifier = Modifier.fillMaxSize(),
                            onBack = pop,
                            onOpenGame = { id -> backStack.add(AppRoute.Game(gameId = id, startInReplayMode = true)) },
                        )
                    }
                    entry<AppRoute.Game> { route ->
                        GameRouteWithShell(
                            gameId = route.gameId,
                            startInReplayMode = route.startInReplayMode,
                            modifier = Modifier.fillMaxSize(),
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
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(title = { Text("登录") })
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthEmailWithShell(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    var topActions by remember { mutableStateOf<(@Composable RowScope.() -> Unit)?>(null) }
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text("邮箱") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = { topActions?.invoke(this) },
            )
        },
    ) { innerPadding ->
        AuthEmailRoute(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            onNext = onNext,
            setTopBarActions = { topActions = it },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthPasswordWithShell(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
) {
    var topActions by remember { mutableStateOf<(@Composable RowScope.() -> Unit)?>(null) }
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text("密码") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = { topActions?.invoke(this) },
            )
        },
    ) { innerPadding ->
        AuthPasswordRoute(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            onDone = {},
            setTopBarActions = { topActions = it },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeRouteWithShell(
    modifier: Modifier = Modifier,
    avatarUrl: String? = null,
    onOpenHistory: () -> Unit,
    onOpenProfile: () -> Unit,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = {},
                actions = {
                    IconButton(onClick = onOpenHistory) {
                        Icon(Icons.Filled.History, contentDescription = "对局历史")
                    }
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
    ) { innerPadding ->
        FriendsScreen(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GameHistoryRouteWithShell(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onOpenGame: (String) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text("对局历史") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { innerPadding ->
        GameHistoryScreen(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            onOpenGame = onOpenGame,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GameRouteWithShell(
    gameId: String,
    startInReplayMode: Boolean,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
    ) { innerPadding ->
        GameScreen(
            gameId = gameId,
            startInReplayMode = startInReplayMode,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileRouteWithShell(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text("个人信息") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { innerPadding ->
        cn.verlu.cnchess.presentation.profile.ProfileScreen(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        )
    }
}

sealed interface AppRoute : NavKey {
    @Serializable
    data object Auth : AppRoute

    @Serializable
    data object AuthEmail : AppRoute

    @Serializable
    data object AuthPassword : AppRoute

    @Serializable
    data object Home : AppRoute

    @Serializable
    data object Profile : AppRoute

    @Serializable
    data object GameHistory : AppRoute

    @Serializable
    data class Game(
        val gameId: String,
        val startInReplayMode: Boolean = false,
    ) : AppRoute
}
