package cn.verlu.memory.presentation.navigation

import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import cn.verlu.memory.presentation.auth.ui.AuthSessionLoadingOverlay
import cn.verlu.memory.presentation.auth.ui.UpdatePasswordDialog
import cn.verlu.memory.presentation.auth.vm.AuthEventManager
import cn.verlu.memory.presentation.auth.vm.AuthSessionViewModel
import cn.verlu.memory.presentation.lifestream.ui.LifeStreamScreen
import cn.verlu.memory.presentation.update.AppUpdateGate
import kotlinx.serialization.Serializable

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
}

private fun isAuthRoute(route: NavKey?): Boolean {
    val r = route as? MemoryRoute ?: return false
    return r == MemoryRoute.Auth || r == MemoryRoute.AuthEmail || r == MemoryRoute.AuthPassword
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryNavApp(
    modifier: Modifier = Modifier,
) {
    val backStack = rememberNavBackStack(MemoryRoute.Auth)
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
            if (justLoggedIn && isAuthRoute(backStack.lastOrNull())) {
                while (backStack.isNotEmpty()) backStack.removeLastOrNull()
                backStack.add(MemoryRoute.Home)
                snackbarHostState.showSnackbar("登录成功")
            } else if (backStack.lastOrNull() == null) {
                backStack.add(MemoryRoute.Home)
            }
            return@LaunchedEffect
        }
        if (!isAuthRoute(backStack.lastOrNull())) {
            while (backStack.isNotEmpty()) backStack.removeLastOrNull()
            backStack.add(MemoryRoute.Auth)
        }
    }

    CompositionLocalProvider(LocalSnackbarHostState provides snackbarHostState) {
        Scaffold(
            modifier = modifier,
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0),
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
            AuthSessionLoadingOverlay(modifier = Modifier.fillMaxSize())
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
                        AuthRoute(
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
                        LifeStreamScreen(
                            onSignOut = { authSessionVm.signOut() },
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
private fun AuthEmailWithShell(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    var topBarActions by remember { mutableStateOf<(@Composable RowScope.() -> Unit)?>(null) }
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            CenterAlignedTopAppBar(
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
        contentWindowInsets = WindowInsets(0),
        topBar = {
            CenterAlignedTopAppBar(
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
