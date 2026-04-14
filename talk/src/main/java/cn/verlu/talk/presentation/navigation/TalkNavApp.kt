package cn.verlu.talk.presentation.navigation

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.PeopleOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import cn.verlu.talk.presentation.auth.ui.AuthEmailRoute
import cn.verlu.talk.presentation.auth.ui.AuthPasswordRoute
import cn.verlu.talk.presentation.auth.ui.AuthRoute
import cn.verlu.talk.presentation.auth.ui.UpdatePasswordDialog
import cn.verlu.talk.presentation.auth.vm.AuthEventManager
import cn.verlu.talk.presentation.auth.vm.AuthSessionViewModel
import cn.verlu.talk.presentation.chat.ChatRoomScreen
import cn.verlu.talk.presentation.contacts.ContactsViewModel
import cn.verlu.talk.presentation.contacts.QrScanFriendScreen
import cn.verlu.talk.presentation.contacts.QrScanFriendViewModel
import cn.verlu.talk.presentation.home.HomeScreen
import cn.verlu.talk.presentation.profile.ProfileQrDialog
import cn.verlu.talk.presentation.profile.ProfileScreen
import cn.verlu.talk.presentation.ui.TalkLoadingIndicator
import coil3.compose.AsyncImage

import kotlinx.coroutines.launch
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

/**
 * 顶栏 / 底栏随路由包在各自 [NavDisplay] entry 的 [Scaffold] 里，避免「全局壳」在 push 时
 * 因 topBar/bottomBar 瞬间消失导致 innerPadding 突变，退场中的首页内容被挤到屏幕顶端再滑动消失。
 *
 * 仅保留一层最外 [Scaffold] 托管 [SnackbarHost]（不参与首页顶栏布局）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TalkNavApp(modifier: Modifier = Modifier) {
    val backStack = rememberNavBackStack(AppRoute.Home)
    val pop: () -> Unit = { backStack.removeLastOrNull() }

    var homeTabIndex by rememberSaveable { mutableIntStateOf(0) }

    val authSessionVm: AuthSessionViewModel = hiltViewModel()
    val authState by authSessionVm.state.collectAsStateWithLifecycle()

    // Activity 作用域的 QrScan ViewModel，Home 和 QrScan 页共享状态：
    // 扫到结果后立刻 pop 回 Home，Home 监听 scannedProfile 弹底部 sheet。
    val qrScanViewModel: QrScanFriendViewModel = hiltViewModel()

    val snackbarHostState = remember { SnackbarHostState() }
    var prevAuthenticated by remember { mutableStateOf<Boolean?>(null) }
    val showPasswordResetDialog by AuthEventManager.showPasswordResetDialog.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val rootView = LocalView.current
    val coroutineScope = rememberCoroutineScope()
    var pendingQrScanNavigation by remember { mutableStateOf(false) }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (pendingQrScanNavigation) {
            pendingQrScanNavigation = false
            if (granted) {
                backStack.add(AppRoute.QrScanFriend)
            } else {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("需要相机权限才能扫码")
                }
            }
        }
    }

    val avatarUrl = authState.user?.userMetadata
        ?.get("avatar_url")?.toString()
        ?.trim('"')
        ?.takeIf { it.isNotBlank() && it != "null" }

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
                        val contactsViewModel = hiltViewModel<ContactsViewModel>()
                        val navigateToQrScan: () -> Unit = {
                            when (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)) {
                                PackageManager.PERMISSION_GRANTED -> {
                                    backStack.add(AppRoute.QrScanFriend)
                                }

                                else -> {
                                    pendingQrScanNavigation = true
                                    rootView.post {
                                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                    }
                                }
                            }
                        }
                        HomeRouteWithShell(
                            modifier = Modifier.fillMaxSize(),
                            homeTabIndex = homeTabIndex,
                            onHomeTabIndex = { homeTabIndex = it },
                            avatarUrl = avatarUrl,
                            contactsViewModel = contactsViewModel,
                            qrScanViewModel = qrScanViewModel,
                            onRequestAddFriend = {
                                contactsViewModel.showAddFriendDialog()
                                homeTabIndex = 1
                            },
                            onNavigateToChat = { roomId -> backStack.add(AppRoute.ChatRoom(roomId)) },
                            onNavigateToProfile = { backStack.add(AppRoute.Profile) },
                            onNavigateToQrScan = navigateToQrScan,
                            onNavigateToContactsTab = { homeTabIndex = 1 },
                        )
                    }
                    entry<AppRoute.ChatRoom> { route ->
                        ChatRoomScreen(
                            roomId = route.roomId,
                            modifier = Modifier.fillMaxSize(),
                            onBack = pop,
                        )
                    }
                    entry<AppRoute.Profile> {
                        ProfileRouteWithShell(
                            modifier = Modifier.fillMaxSize(),
                            onBack = pop,
                        )
                    }
                    entry<AppRoute.QrScanFriend> {
                        QrScanRouteWithShell(
                            modifier = Modifier.fillMaxSize(),
                            onBack = pop,
                            qrScanViewModel = qrScanViewModel,
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HomeRouteWithShell(
    modifier: Modifier = Modifier,
    homeTabIndex: Int,
    onHomeTabIndex: (Int) -> Unit,
    avatarUrl: String?,
    contactsViewModel: ContactsViewModel,
    qrScanViewModel: QrScanFriendViewModel,
    onRequestAddFriend: () -> Unit,
    onNavigateToChat: (String) -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToQrScan: () -> Unit,
    onNavigateToContactsTab: () -> Unit,
) {
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topAppBarState)
    val title = if (homeTabIndex == 0) "聊天" else "联系人"

    val qrState by qrScanViewModel.state.collectAsStateWithLifecycle()
    val qrSheetState = rememberModalBottomSheetState()
    val snackbar = LocalSnackbarHostState.current

    // 扫码成功后在这里弹出 sheet，扫码页已 pop 回到这里
    if (qrState.scannedProfile != null) {
        ModalBottomSheet(
            onDismissRequest = { qrScanViewModel.dismissProfile() },
            sheetState = qrSheetState,
        ) {
            val profile = qrState.scannedProfile!!
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (profile.avatarUrl != null) {
                    AsyncImage(
                        model = profile.avatarUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Icon(
                        Icons.Default.AccountCircle,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = profile.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (profile.email != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = profile.email,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = { qrScanViewModel.sendRequest() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !qrState.isSending,
                ) {
                    if (qrState.isSending) {
                        TalkLoadingIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (qrState.isSending) "发送中…" else "发送好友申请")
                }
            }
        }
    }

    // 发送成功（含「已是好友」等软失败）→ 关闭 sheet 并提示
    LaunchedEffect(qrState.success) {
        if (!qrState.success) return@LaunchedEffect
        val msg = qrState.successMessage ?: "好友申请已发送"
        qrScanViewModel.dismissProfile()
        qrScanViewModel.clearSuccess()
        snackbar.showSnackbar(msg)
    }

    // 扫码错误提示
    LaunchedEffect(qrState.error) {
        val err = qrState.error ?: return@LaunchedEffect
        snackbar.showSnackbar(err)
        qrScanViewModel.clearError()
    }

    LaunchedEffect(homeTabIndex) {
        topAppBarState.heightOffset = 0f
        topAppBarState.contentOffset = 0f
    }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            LargeTopAppBar(
                title = { Text(title) },
                scrollBehavior = scrollBehavior,
                actions = {
                    if (homeTabIndex == 0) {
                        IconButton(onClick = onNavigateToQrScan) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = "扫一扫")
                        }
                    }
                    if (homeTabIndex == 1) {
                        IconButton(onClick = onRequestAddFriend) {
                            Icon(Icons.Default.PersonAdd, contentDescription = "添加好友")
                        }
                    }
                    IconButton(onClick = onNavigateToProfile) {
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
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = homeTabIndex == 0,
                    onClick = { onHomeTabIndex(0) },
                    icon = {
                        Icon(
                            if (homeTabIndex == 0) Icons.Filled.ChatBubble
                            else Icons.Outlined.ChatBubbleOutline,
                            contentDescription = "聊天",
                        )
                    },
                    label = { Text("聊天") },
                )
                NavigationBarItem(
                    selected = homeTabIndex == 1,
                    onClick = { onHomeTabIndex(1) },
                    icon = {
                        Icon(
                            if (homeTabIndex == 1) Icons.Filled.People
                            else Icons.Outlined.PeopleOutline,
                            contentDescription = "联系人",
                        )
                    },
                    label = { Text("联系人") },
                )
            }
        },
    ) { innerPadding ->
        HomeScreen(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            selectedTab = homeTabIndex,
            contactsViewModel = contactsViewModel,
            onNavigateToChat = onNavigateToChat,
            onNavigateToContacts = onNavigateToContactsTab,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileRouteWithShell(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
) {
    val authVm: AuthSessionViewModel = hiltViewModel()
    val authState by authVm.state.collectAsStateWithLifecycle()
    var showQrDialog by remember { mutableStateOf(false) }

    val user = authState.user
    val profileUid = user?.id.orEmpty()
    val profileDisplayName = user?.userMetadata?.get("full_name")?.toString()?.trim('"')
        ?.takeIf { it.isNotBlank() && it != "null" }
        ?: user?.userMetadata?.get("user_name")?.toString()?.trim('"')
            ?.takeIf { it.isNotBlank() && it != "null" }
        ?: user?.email?.substringBefore("@")
        ?: "用户"

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = { Text("个人信息") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
                actions = {
                    if (profileUid.isNotBlank()) {
                        IconButton(onClick = { showQrDialog = true }) {
                            Icon(Icons.Filled.QrCode, contentDescription = "我的二维码")
                        }
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

    if (showQrDialog && profileUid.isNotBlank()) {
        ProfileQrDialog(
            uid = profileUid,
            displayName = profileDisplayName,
            onDismiss = { showQrDialog = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QrScanRouteWithShell(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    qrScanViewModel: QrScanFriendViewModel,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = { Text("扫一扫") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        QrScanFriendScreen(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            onBack = onBack,
            viewModel = qrScanViewModel,
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
    data class ChatRoom(val roomId: String) : AppRoute

    @Serializable
    data object Profile : AppRoute

    @Serializable
    data object QrScanFriend : AppRoute
}
