package cn.verlu.sync.presentation.navigation

import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.WbCloudy
import androidx.compose.material3.ExperimentalMaterial3Api
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

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import cn.verlu.sync.presentation.auth.ui.AuthEmailRoute
import cn.verlu.sync.presentation.auth.ui.AuthPasswordRoute
import cn.verlu.sync.presentation.auth.ui.AuthRoute
import cn.verlu.sync.presentation.auth.ui.QrLoginRoute
import cn.verlu.sync.presentation.auth.ui.UpdatePasswordDialog
import cn.verlu.sync.presentation.auth.vm.AuthEventManager
import cn.verlu.sync.presentation.auth.vm.AuthSessionViewModel
import cn.verlu.sync.presentation.home.ui.HomeRoute
import cn.verlu.sync.presentation.profile.ui.UserProfileRoute
import cn.verlu.sync.presentation.screentime.ui.ScreenTimeRoute
import cn.verlu.sync.presentation.temperature.ui.TemperatureRoute
import cn.verlu.sync.presentation.weather.ui.WeatherRoute
import coil3.compose.AsyncImage
import kotlinx.serialization.Serializable

private fun isAuthSubFlow(route: NavKey?): Boolean {
    val r = route as? AppRoute ?: return false
    return r == AppRoute.Auth || r == AppRoute.AuthEmail || r == AppRoute.AuthPassword
}

val LocalSnackbarHostState = compositionLocalOf<SnackbarHostState> {
    error("No SnackbarHostState provided")
}

/**
 * 单壳 [Scaffold]：标题与返回按钮随 [backStack] 顶层路由变化（单 Activity 内只换内容区）。
 * 切换动画由 NavDisplay 的 transitionSpec / popTransitionSpec 配置（见 Android 文档 Animate between destinations）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncNavApp(modifier: Modifier = Modifier) {
    val backStack = rememberNavBackStack(AppRoute.Home)
    val pop: () -> Unit = { backStack.removeLastOrNull() }
    val top = backStack.lastOrNull() ?: AppRoute.Home
    val title = when (top) {
        AppRoute.Home -> "电量"
        AppRoute.ScreenTime -> "屏幕使用时长"
        AppRoute.Temperature -> "温度"
        AppRoute.Weather -> "天气"
        AppRoute.Auth -> "登录"
        AppRoute.AuthEmail -> "邮箱"
        AppRoute.AuthPassword -> "密码"
        AppRoute.Profile -> "个人信息"
        AppRoute.QrScan -> "扫码授权"
        is AppRoute.SSOAuthorize -> "应用授权"
        else -> ""
    }
    val canPop = backStack.size > 1

    val authSessionVm: AuthSessionViewModel = hiltViewModel()
    val authState by authSessionVm.state.collectAsStateWithLifecycle()

    var topBarActions by remember {
        mutableStateOf<Map<AppRoute, @Composable RowScope.() -> Unit>>(
            emptyMap()
        )
    }
    var prevAuthenticated by remember { mutableStateOf<Boolean?>(null) }
    val showPasswordResetDialog by AuthEventManager.showPasswordResetDialog.collectAsStateWithLifecycle()
    val pendingSso by AuthEventManager.pendingSsoAuthorize.collectAsStateWithLifecycle()

    LaunchedEffect(pendingSso) {
        val req = pendingSso ?: return@LaunchedEffect
        val sessionId = req.sessionId
        if (sessionId.isNotBlank()) {
            val currentTop = backStack.lastOrNull() ?: AppRoute.Home
            if (currentTop !is AppRoute.SSOAuthorize ||
                currentTop.sessionId != sessionId ||
                currentTop.returnPackage != req.returnPackage
            ) {
                backStack.add(AppRoute.SSOAuthorize(sessionId, req.returnPackage))
            }
            AuthEventManager.pendingSsoAuthorize.value = null
        }
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
            }
            return@LaunchedEffect
        }

        val current = backStack.lastOrNull()
        if (isAuthSubFlow(current)) return@LaunchedEffect

        // 深链进入的「应用授权」或扫码页：不要清空栈，否则会丢掉 returnPkg，成功页可能错误回到其它 App
        if (current is AppRoute.SSOAuthorize || current is AppRoute.QrScan) return@LaunchedEffect

        while (backStack.isNotEmpty()) backStack.removeLastOrNull()
        backStack.add(AppRoute.Auth)
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val snackbarHostState = remember { SnackbarHostState() }

    CompositionLocalProvider(
        LocalSnackbarHostState provides snackbarHostState
    ) {
        Scaffold(
            modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                LargeTopAppBar(
                    title = { Text(title) },
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        if (canPop) {
                            IconButton(onClick = pop) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "返回"
                                )
                            }
                        }
                    },
                    actions = {
                        val onAuthScreens = top == AppRoute.Auth ||
                                top == AppRoute.AuthEmail ||
                                top == AppRoute.AuthPassword

                        if (authState.isAuthenticated && !onAuthScreens && top != AppRoute.Profile && top != AppRoute.QrScan) {
                            val avatarUrl =
                                authState.user?.userMetadata?.get("avatar_url")?.toString()
                                    ?.trim('"')
                            IconButton(onClick = { backStack.add(AppRoute.Profile) }) {
                                if (!avatarUrl.isNullOrEmpty()) {
                                    AsyncImage(
                                        model = avatarUrl,
                                        contentDescription = "个人信息",
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.AccountCircle,
                                        contentDescription = "个人信息",
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }
                        } else if (authState.isAuthenticated && top == AppRoute.Profile) {
                            // 扫码按钮只在个人信息页右上角显示
                            IconButton(onClick = { backStack.add(AppRoute.QrScan) }) {
                                Icon(
                                    Icons.Default.QrCodeScanner,
                                    contentDescription = "扫码授权登录"
                                )
                            }
                        }
                        topBarActions[top]?.invoke(this)
                    }
                )
            },
            bottomBar = {
                if (top == AppRoute.Home || top == AppRoute.ScreenTime || top == AppRoute.Temperature || top == AppRoute.Weather) {
                    NavigationBar {
                        NavigationBarItem(
                            selected = top == AppRoute.Home,
                            onClick = {
                                if (top != AppRoute.Home) {
                                    while (backStack.isNotEmpty()) backStack.removeLastOrNull()
                                    backStack.add(AppRoute.Home)
                                }
                            },
                            icon = { Icon(Icons.Default.BatteryFull, contentDescription = "电量") },
                            label = { Text("电量") }
                        )
                        NavigationBarItem(
                            selected = top == AppRoute.Temperature,
                            onClick = {
                                if (top != AppRoute.Temperature) {
                                    while (backStack.isNotEmpty()) backStack.removeLastOrNull()
                                    backStack.add(AppRoute.Temperature)
                                }
                            },
                            icon = { Icon(Icons.Default.Thermostat, contentDescription = "温度") },
                            label = { Text("温度") }
                        )
                        NavigationBarItem(
                            selected = top == AppRoute.ScreenTime,
                            onClick = {
                                if (top != AppRoute.ScreenTime) {
                                    while (backStack.isNotEmpty()) backStack.removeLastOrNull()
                                    backStack.add(AppRoute.ScreenTime)
                                }
                            },
                            icon = { Icon(Icons.Default.Timer, contentDescription = "使用时长") },
                            label = { Text("时长") }
                        )
                        NavigationBarItem(
                            selected = top == AppRoute.Weather,
                            onClick = {
                                if (top != AppRoute.Weather) {
                                    while (backStack.isNotEmpty()) backStack.removeLastOrNull()
                                    backStack.add(AppRoute.Weather)
                                }
                            },
                            icon = { Icon(Icons.Default.WbCloudy, contentDescription = "天气") },
                            label = { Text("天气") }
                        )
                    }
                }
            }
        ) { innerPadding ->
            if (showPasswordResetDialog) {
                UpdatePasswordDialog(
                    onDismiss = {
                        AuthEventManager.showPasswordResetDialog.value = false
                    }
                )
            }

            NavDisplay(
                backStack = backStack,
                onBack = pop,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                // 旧页整页滑出，避免只移 1/4 导致过渡期间仍透出下层（看起来像叠在首页上）
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
                    entry<AppRoute.Home> {
                        HomeRoute(
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    entry<AppRoute.ScreenTime> {
                        ScreenTimeRoute(modifier = Modifier.fillMaxSize())
                    }
                    entry<AppRoute.Temperature> {
                        TemperatureRoute(modifier = Modifier.fillMaxSize())
                    }
                    entry<AppRoute.Weather> {
                        WeatherRoute(modifier = Modifier.fillMaxSize())
                    }
                    entry<AppRoute.Profile> {
                        UserProfileRoute(modifier = Modifier.fillMaxSize())
                    }
                    entry<AppRoute.QrScan> {
                        QrLoginRoute(
                            modifier = Modifier.fillMaxSize(),
                            returnToPackage = null,
                            onFinished = pop
                        )
                    }
                    entry<AppRoute.SSOAuthorize> { route ->
                        QrLoginRoute(
                            modifier = Modifier.fillMaxSize(),
                            sessionId = route.sessionId,
                            returnToPackage = route.returnPackage,
                            onFinished = pop
                        )
                    }
                    entry<AppRoute.Auth> {
                        AuthRoute(
                            modifier = Modifier.fillMaxSize(),
                            onOpenEmailLogin = { backStack.add(AppRoute.AuthEmail) },
                            onOpenEmailRegister = {
                                // 进入邮箱页前先切换模式为注册
                                backStack.add(AppRoute.AuthEmail)
                            }
                        )
                    }
                    entry<AppRoute.AuthEmail> {
                        AuthEmailRoute(
                            modifier = Modifier.fillMaxSize(),
                            onNext = { backStack.add(AppRoute.AuthPassword) },
                            setTopBarActions = { action ->
                                topBarActions =
                                    if (action == null) topBarActions - AppRoute.AuthEmail else topBarActions + (AppRoute.AuthEmail to action)
                            }
                        )
                    }
                    entry<AppRoute.AuthPassword> {
                        AuthPasswordRoute(
                            modifier = Modifier.fillMaxSize(),
                            onDone = { /* submit 之后由 sessionStatus 自动跳转 */ },
                            setTopBarActions = { action ->
                                topBarActions =
                                    if (action == null) topBarActions - AppRoute.AuthPassword else topBarActions + (AppRoute.AuthPassword to action)
                            }
                        )
                    }
                }
            )
        }
    }
}

sealed interface AppRoute : NavKey {
    @Serializable
    data object Home : AppRoute

    @Serializable
    data object ScreenTime : AppRoute

    @Serializable
    data object Temperature : AppRoute

    @Serializable
    data object Weather : AppRoute

    @Serializable
    data object Auth : AppRoute

    @Serializable
    data object AuthEmail : AppRoute

    @Serializable
    data object AuthPassword : AppRoute

    @Serializable
    data object Profile : AppRoute

    @Serializable
    data object QrScan : AppRoute

    @Serializable
    data class SSOAuthorize(
        val sessionId: String,
        val returnPackage: String? = null,
    ) : AppRoute
}
