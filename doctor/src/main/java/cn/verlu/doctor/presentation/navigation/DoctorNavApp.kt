package cn.verlu.doctor.presentation.navigation

import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import cn.verlu.doctor.presentation.auth.ui.AuthEmailRoute
import cn.verlu.doctor.presentation.auth.ui.AuthPasswordRoute
import cn.verlu.doctor.presentation.auth.ui.AuthRoute
import cn.verlu.doctor.presentation.auth.ui.UpdatePasswordDialog
import cn.verlu.doctor.presentation.auth.vm.AuthEventManager
import cn.verlu.doctor.presentation.auth.vm.AuthSessionViewModel
import cn.verlu.doctor.presentation.herb.ui.HerbArticleDetailScreen
import cn.verlu.doctor.presentation.herb.ui.HerbMainShell
import cn.verlu.doctor.presentation.herb.ui.HerbSearchScreen
import cn.verlu.doctor.presentation.profile.ProfileScreen
import cn.verlu.doctor.presentation.update.AppUpdateGate

import kotlinx.serialization.Serializable

private fun isAuthSubFlow(route: NavKey?): Boolean {
    val r = route as? AppRoute ?: return false
    return r == AppRoute.Auth ||
        r == AppRoute.AuthEmail ||
        r == AppRoute.AuthPassword
}

val LocalSnackbarHostState = compositionLocalOf<SnackbarHostState> {
    error("No SnackbarHostState provided")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoctorNavApp(modifier: Modifier = Modifier) {
    val backStack = rememberNavBackStack(AppRoute.Home)
    val pop: () -> Unit = { backStack.removeLastOrNull() }

    val authSessionVm: AuthSessionViewModel = hiltViewModel()
    val authState by authSessionVm.state.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var prevAuthenticated by remember { mutableStateOf<Boolean?>(null) }
    val showPasswordResetDialog by AuthEventManager.showPasswordResetDialog.collectAsStateWithLifecycle()

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
                snackbarHostState.showSnackbar("授权登录成功 🎉")
            }
            return@LaunchedEffect
        }

        val current = backStack.lastOrNull()
        if (isAuthSubFlow(current)) return@LaunchedEffect
        while (backStack.isNotEmpty()) backStack.removeLastOrNull()
        backStack.add(AppRoute.Auth)
    }

    CompositionLocalProvider(
        LocalSnackbarHostState provides snackbarHostState,
    ) {
        Scaffold(
            modifier = modifier,
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0.dp),
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { snackbarPadding ->
            if (showPasswordResetDialog) {
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
                        HerbMainShell(
                            modifier = Modifier.fillMaxSize(),
                            avatarUrl = avatarUrl,
                            onOpenArticle = { path -> backStack.add(AppRoute.HerbArticle(path)) },
                            onOpenSearch = { backStack.add(AppRoute.HerbSearch) },
                            onOpenProfile = { backStack.add(AppRoute.Profile) },
                        )
                    }
                    entry<AppRoute.Profile> {
                        ProfileRouteWithShell(
                            modifier = Modifier.fillMaxSize(),
                            onBack = pop,
                        )
                    }
                    entry<AppRoute.HerbSearch> {
                        HerbSearchScreen(
                            modifier = Modifier.fillMaxSize(),
                            onBack = pop,
                            onOpenArticle = { path ->
                                // 先弹出搜索页再进详情：返回时到主页，且搜索页销毁后搜索框为空
                                backStack.removeLastOrNull()
                                backStack.add(AppRoute.HerbArticle(path))
                            },
                        )
                    }
                    entry<AppRoute.HerbArticle> { route ->
                        HerbArticleDetailScreen(
                            path = route.path,
                            onBack = pop,
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
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = { Text("邮箱") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
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
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = { Text("密码") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
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
            onDone = { },
            setTopBarActions = { topActions = it },
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
        contentWindowInsets = WindowInsets(0.dp),
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
        ProfileScreen(
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
    data object HerbSearch : AppRoute

    @Serializable
    data class HerbArticle(val path: String) : AppRoute
}
