package cn.verlu.cloud.navigation

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.CurrentScreen
import cafe.adriel.voyager.navigator.Navigator
import cn.verlu.cloud.data.friends.CloudFriendItem
import cn.verlu.cloud.di.AppGraph
import cn.verlu.cloud.domain.auth.OAuthProvider
import cn.verlu.cloud.domain.files.CloudFileItem
import cn.verlu.cloud.isDesktopPlatform
import cn.verlu.cloud.presentation.auth.*
import cn.verlu.cloud.presentation.files.FileExplorerState
import cn.verlu.cloud.presentation.files.IncomingShareBus
import cn.verlu.cloud.presentation.files.ShareStep
import cn.verlu.cloud.presentation.files.DesktopFileDropEffect
import cn.verlu.cloud.presentation.files.UploadItemStatus
import cn.verlu.cloud.presentation.files.rememberFilePicker
import io.github.alexzhirkevich.qrose.rememberQrCodePainter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

private enum class AuthStep { Landing, Email, Password }

/** Voyager persists [Screen] in the Android activity state; keep only primitives / serializable data on screens. */
private val LocalAuthGate = staticCompositionLocalOf<AuthGateState> { error("LocalAuthGate not provided") }
private val LocalAppGraph = staticCompositionLocalOf<AppGraph> { error("LocalAppGraph not provided") }

/** 会话恢复完成前显示，避免先闪登录页再进网盘（与 Talk/Sync 启动策略一致）。 */
private data object SessionLoadingScreen : Screen {
    @Composable
    override fun Content() {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}

@Composable
fun CloudAppRoot(graph: AppGraph) {
    val gate = remember(graph) { AuthGateState(graph.authUseCases) }
    val session by gate.session.collectAsState()

    CompositionLocalProvider(LocalAuthGate provides gate, LocalAppGraph provides graph) {
        Navigator(SessionLoadingScreen) { navigator ->
            LaunchedEffect(Unit) {
                AuthDeepLinkBus.links.collect { url ->
                    gate.handleDeepLink(url)
                }
            }
            LaunchedEffect(session.isInitializing, session.isAuthenticated) {
                if (session.isInitializing) return@LaunchedEffect
                val current = navigator.lastItem
                if (session.isAuthenticated) {
                    val ownerId = session.user?.id.orEmpty()
                    if (ownerId.isNotEmpty() && current !is ExplorerScreen) {
                        navigator.replaceAll(ExplorerScreen(ownerId))
                    }
                } else {
                    if (current !is AuthScreen) {
                        navigator.replaceAll(AuthScreen)
                    }
                }
            }
            CurrentScreen()
        }
    }
}

private object AuthScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val gate = LocalAuthGate.current
        val form by gate.form.collectAsState()
        val desktopQrPayload by gate.desktopQrPayload.collectAsState()
        val desktopQrExpiresAtMs by gate.desktopQrExpiresAtMs.collectAsState()
        var step by rememberSaveable { mutableStateOf(AuthStep.Landing) }
        var desktopQrAutoRequested by rememberSaveable { mutableStateOf(false) }
        var nowMs by remember { mutableStateOf(kotlin.time.Clock.System.now().toEpochMilliseconds()) }
        val snackbarHostState = remember { SnackbarHostState() }

        LaunchedEffect(form.error) {
            form.error?.let {
                snackbarHostState.showSnackbar(it)
                gate.clearFormMessage()
            }
        }
        LaunchedEffect(form.info) {
            form.info?.let {
                snackbarHostState.showSnackbar(it)
                gate.clearFormMessage()
            }
        }
        LaunchedEffect(step, desktopQrPayload, form.isSubmitting) {
            if (isDesktopPlatform() &&
                step == AuthStep.Landing &&
                desktopQrPayload == null &&
                !form.isSubmitting &&
                !desktopQrAutoRequested
            ) {
                desktopQrAutoRequested = true
                gate.requestDesktopQrLogin()
            }
        }
        LaunchedEffect(desktopQrPayload, desktopQrExpiresAtMs, step) {
            while (isDesktopPlatform() && step == AuthStep.Landing && desktopQrPayload != null && desktopQrExpiresAtMs != null) {
                nowMs = kotlin.time.Clock.System.now().toEpochMilliseconds()
                delay(1_000)
            }
        }

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                if (step != AuthStep.Landing) {
                    TopAppBar(
                        title = {},
                        navigationIcon = {
                            IconButton(onClick = { step = AuthStep.Landing }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                        actions = {
                            when (step) {
                                AuthStep.Email -> {
                                    TextButton(
                                        onClick = {
                                            if (form.email.trim().isNotEmpty()) step = AuthStep.Password
                                        },
                                        enabled = !form.isSubmitting && form.email.trim().isNotEmpty(),
                                    ) { Text("Next") }
                                }

                                AuthStep.Password -> {
                                    TextButton(
                                        onClick = { gate.submit() },
                                        enabled = !form.isSubmitting,
                                    ) {
                                        Text(if (form.mode == AuthFormMode.Register) "Sign up" else "Sign in")
                                    }
                                }

                                else -> Unit
                            }
                        },
                    )
                }
            },
        ) { paddingValues ->
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                when (step) {
                    AuthStep.Landing -> AuthLanding(
                        isSubmitting = form.isSubmitting,
                        desktopQrPayload = desktopQrPayload,
                        desktopQrRemainSec = ((desktopQrExpiresAtMs ?: 0L) - nowMs).coerceAtLeast(0L) / 1000L,
                        onRequestDesktopQr = { gate.requestDesktopQrLogin() },
                        onClearDesktopQr = { gate.clearDesktopQrPayload() },
                        onEmailLogin = {
                            gate.setMode(AuthFormMode.Login)
                            step = AuthStep.Email
                        },
                        onRegister = {
                            gate.setMode(AuthFormMode.Register)
                            step = AuthStep.Email
                        },
                        onGitHub = {
                            gate.signInOAuth(
                                provider = OAuthProvider.GitHub,
                                onStarted = {},
                                onError = {},
                            )
                        },
                        onGoogle = {
                            gate.signInOAuth(
                                provider = OAuthProvider.Google,
                                onStarted = {},
                                onError = {},
                            )
                        },
                    )

                    AuthStep.Email -> AuthEmailStep(
                        email = form.email,
                        isRegisterMode = form.mode == AuthFormMode.Register,
                        isSubmitting = form.isSubmitting,
                        onEmailChange = gate::onEmailChange,
                        onNext = { if (form.email.trim().isNotEmpty()) step = AuthStep.Password },
                    )

                    AuthStep.Password -> AuthPasswordStep(
                        password = form.password,
                        isRegisterMode = form.mode == AuthFormMode.Register,
                        isSubmitting = form.isSubmitting,
                        onPasswordChange = gate::onPasswordChange,
                        onSubmit = { gate.submit() },
                        onForgotPassword = { gate.resetPassword() },
                    )
                }

                if (form.isSubmitting) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthLanding(
    isSubmitting: Boolean,
    desktopQrPayload: String?,
    desktopQrRemainSec: Long,
    onRequestDesktopQr: () -> Unit,
    onClearDesktopQr: () -> Unit,
    onEmailLogin: () -> Unit,
    onRegister: () -> Unit,
    onGitHub: () -> Unit,
    onGoogle: () -> Unit,
) {
    val githubPainter = githubIconPainter()
    val googlePainter = googleIconPainter()

    if (isDesktopPlatform()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            MaterialTheme.colorScheme.surface,
                        ),
                    ),
                )
                .padding(28.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val panelModifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(0.8f)
                    .shadow(8.dp, RoundedCornerShape(20.dp), spotColor = Color.Black.copy(alpha = 0.12f))
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                        RoundedCornerShape(20.dp)
                    )
                Surface(
                    modifier = panelModifier,
                    shape = RoundedCornerShape(20.dp),
                    tonalElevation = 1.dp,
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("扫码登录", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(12.dp))
                            desktopQrPayload?.let { payload ->
                                val isExpired = desktopQrRemainSec <= 0L
                                var refreshAnimating by remember(payload, isExpired) { mutableStateOf(false) }
                                val refreshRotate by animateFloatAsState(
                                    targetValue = if (refreshAnimating) 360f else 0f,
                                    animationSpec = tween(durationMillis = 700),
                                    label = "qrRefreshRotate",
                                )
                                LaunchedEffect(refreshAnimating, refreshRotate) {
                                    if (refreshAnimating && refreshRotate >= 359f) {
                                        refreshAnimating = false
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .size(236.dp)
                                        .background(
                                            MaterialTheme.colorScheme.surface,
                                            AbsoluteRoundedCornerShape(16.dp)
                                        )
                                        .border(
                                            2.dp,
                                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                                            AbsoluteRoundedCornerShape(16.dp)
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (isExpired) {
                                        Surface(
                                            modifier = Modifier.size(208.dp),
                                            shape = AbsoluteRoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                            border = BorderStroke(
                                                1.dp,
                                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                                            ),
                                        ) {
                                            Column(
                                                modifier = Modifier.fillMaxSize().padding(12.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center,
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.CloudOff,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(40.dp),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                                Spacer(Modifier.height(8.dp))
                                                Text(
                                                    text = "二维码已过期",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                                Spacer(Modifier.height(10.dp))
                                                TextButton(
                                                    onClick = {
                                                        refreshAnimating = true
                                                        onRequestDesktopQr()
                                                    },
                                                    enabled = !isSubmitting,
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Refresh,
                                                        contentDescription = null,
                                                        modifier = Modifier
                                                            .size(16.dp)
                                                            .graphicsLayer { rotationZ = refreshRotate },
                                                    )
                                                    Spacer(Modifier.width(6.dp))
                                                    Text("刷新二维码")
                                                }
                                            }
                                        }
                                    } else {
                                        Image(
                                            painter = rememberQrCodePainter(payload),
                                            contentDescription = "登录二维码",
                                            modifier = Modifier.size(208.dp),
                                        )
                                    }
                                }
                            } ?: Box(
                                modifier = Modifier.size(236.dp),
                                contentAlignment = Alignment.Center,
                            ) { CircularProgressIndicator() }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }

                Surface(
                    modifier = panelModifier,
                    shape = RoundedCornerShape(20.dp),
                    tonalElevation = 1.dp,
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            "Cloud",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                letterSpacing = 0.8.sp,
                            ),
                            fontWeight = FontWeight.ExtraBold,
                        )
                        Spacer(Modifier.height(28.dp))
                        Button(
                            onClick = onEmailLogin,
                            modifier = Modifier.fillMaxWidth().height(54.dp),
                            shape = RoundedCornerShape(14.dp),
                            enabled = !isSubmitting,
                        ) {
                            Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("邮箱与密码登录", fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = onRegister,
                            modifier = Modifier.fillMaxWidth().height(54.dp),
                            shape = RoundedCornerShape(14.dp),
                            enabled = !isSubmitting,
                            border = androidx.compose.foundation.BorderStroke(
                                1.4.dp,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                            ),
                        ) {
                            Text(
                                "创建账号",
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        // 桌面端暂时隐藏第三方 OAuth 入口（GitHub / Google）
                    }
                }
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(64.dp))

        Icon(
            imageVector = Icons.Default.Cloud,
            contentDescription = "Cloud",
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary,
        )

        Spacer(Modifier.height(24.dp))

        Text(
            text = "Cloud",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Secure cross-platform cloud drive",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(56.dp))

        Button(
            onClick = onEmailLogin,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            enabled = !isSubmitting,
        ) {
            Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Sign in with email", fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            onClick = onRegister,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            enabled = !isSubmitting,
        ) {
            Text("Create account", fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(32.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HorizontalDivider(
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            Text(
                text = "or",
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            HorizontalDivider(
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }

        Spacer(Modifier.height(32.dp))

        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (githubPainter != null) {
                IconButton(
                    onClick = onGitHub,
                    modifier = Modifier.size(48.dp),
                    enabled = !isSubmitting,
                ) {
                    Image(
                        painter = githubPainter,
                        contentDescription = "Sign in with GitHub",
                        modifier = Modifier.size(40.dp),
                    )
                }
            } else {
                OutlinedButton(onClick = onGitHub, enabled = !isSubmitting) {
                    Text("GitHub")
                }
            }

            Spacer(Modifier.width(32.dp))

            if (googlePainter != null) {
                IconButton(
                    onClick = onGoogle,
                    modifier = Modifier.size(48.dp),
                    enabled = !isSubmitting,
                ) {
                    Image(
                        painter = googlePainter,
                        contentDescription = "Sign in with Google",
                        modifier = Modifier.size(40.dp),
                    )
                }
            } else {
                OutlinedButton(onClick = onGoogle, enabled = !isSubmitting) {
                    Text("Google")
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun AuthEmailStep(
    email: String,
    isRegisterMode: Boolean,
    isSubmitting: Boolean,
    onEmailChange: (String) -> Unit,
    onNext: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = if (isRegisterMode) "Sign up" else "Sign in",
            style = MaterialTheme.typography.titleMedium,
        )

        OutlinedTextField(
            value = email,
            onValueChange = onEmailChange,
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            singleLine = true,
            enabled = !isSubmitting,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Done,
            ),
        )
    }
}

@Composable
private fun AuthPasswordStep(
    password: String,
    isRegisterMode: Boolean,
    isSubmitting: Boolean,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onForgotPassword: () -> Unit,
) {
    var passwordVisible by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = if (isRegisterMode) "Set password" else "Enter password",
            style = MaterialTheme.typography.titleMedium,
        )

        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            singleLine = true,
            enabled = !isSubmitting,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                        contentDescription = if (passwordVisible) "Hide password" else "Show password",
                    )
                }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
        )

        if (!isRegisterMode) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onForgotPassword, enabled = !isSubmitting) {
                    Text(
                        text = "Forgot password?",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

// ── Explorer Screen ───────────────────────────────────────────────────────────

private data class ExplorerScreen(
    private val ownerId: String,
) : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val gate = LocalAuthGate.current
        val graph = LocalAppGraph.current
        val explorerState = remember(ownerId) {
            FileExplorerState(ownerId, graph.fileRepository, graph.friendRepository)
        }
        val ui by explorerState.state.collectAsState()
        val snackbar = remember { SnackbarHostState() }
        val explorerScope = rememberCoroutineScope()

        // 上传完成 / 删除 / 错误 Toast
        LaunchedEffect(ui.toast) {
            ui.toast?.let { snackbar.showSnackbar(it); explorerState.clearToast() }
        }
        LaunchedEffect(ui.error) {
            ui.error?.let { snackbar.showSnackbar(it); explorerState.clearError() }
        }
        LaunchedEffect(ui.highlightedFolderName) {
            if (ui.highlightedFolderName != null) {
                delay(1800)
                explorerState.clearFolderHighlight()
            }
        }

        // 重命名对话框
        var renameTarget by remember { mutableStateOf<CloudFileItem?>(null) }
        var renameText by remember { mutableStateOf("") }
        var deleteTargets by remember { mutableStateOf<List<CloudFileItem>>(emptyList()) }
        var selectedFileIds by remember { mutableStateOf<Set<String>>(emptySet()) }
        var showLogoutConfirm by remember { mutableStateOf(false) }
        var isDesktopDragOver by remember { mutableStateOf(false) }
        var showCreateFolderDialog by remember { mutableStateOf(false) }
        var newFolderName by remember { mutableStateOf("") }
        var pendingDroppedFiles by remember { mutableStateOf<List<cn.verlu.cloud.presentation.files.FilePickResult>>(emptyList()) }
        var pendingSharedFiles by remember { mutableStateOf<List<cn.verlu.cloud.presentation.files.FilePickResult>>(emptyList()) }
        var fileDetailTarget by remember { mutableStateOf<CloudFileItem?>(null) }
        var moveTargets by remember { mutableStateOf<List<CloudFileItem>>(emptyList()) }
        var movePickerPrefix by remember { mutableStateOf("") }
        val desktop = isDesktopPlatform()

        val pickFile = rememberFilePicker { picks ->
            if (picks.isNotEmpty()) {
                explorerState.uploadFiles(picks)
            }
        }
        LaunchedEffect(Unit) {
            IncomingShareBus.pollAll().takeIf { it.isNotEmpty() }?.let {
                pendingSharedFiles = it
                snackbar.showSnackbar("已接收来自系统分享的 ${it.size} 个文件")
            }
            IncomingShareBus.changed.collect {
                val picks = IncomingShareBus.pollAll()
                if (picks.isNotEmpty()) {
                    pendingSharedFiles = picks
                    snackbar.showSnackbar("已接收来自系统分享的 ${picks.size} 个文件")
                }
            }
        }
        DesktopFileDropEffect(
            onFilesDropped = { picks -> pendingDroppedFiles = picks },
            onDragStateChange = { isDesktopDragOver = desktop && it },
        )

        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = if (ui.currentPrefix.isNotEmpty()) "/ ${ui.currentPrefix.trimEnd('/')}" else "根目录",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (ui.highlightedFolderName != null) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        if (ui.currentPrefix.isNotEmpty()) {
                            IconButton(onClick = { explorerState.navigateUp() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回上级")
                            }
                        }
                    },
                    actions = {
                        if (selectedFileIds.isNotEmpty()) {
                            val movableSelected =
                                ui.files.any { selectedFileIds.contains(it.id) && !it.isDir }
                            TextButton(
                                onClick = {
                                    val movable =
                                        ui.files.filter { selectedFileIds.contains(it.id) && !it.isDir }
                                    if (movable.isEmpty()) {
                                        explorerScope.launch {
                                            snackbar.showSnackbar("请选择要移动的文件（文件夹请取消勾选）")
                                        }
                                    } else {
                                        moveTargets = movable
                                        movePickerPrefix = ui.currentPrefix
                                    }
                                },
                                enabled = movableSelected,
                            ) {
                                Icon(Icons.Default.Folder, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("移动到")
                            }
                            TextButton(
                                onClick = {
                                    deleteTargets = ui.files.filter { selectedFileIds.contains(it.id) }
                                },
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                Spacer(Modifier.width(6.dp))
                                Text("删除所选(${selectedFileIds.size})", color = MaterialTheme.colorScheme.error)
                            }
                        }
                        TextButton(onClick = { showCreateFolderDialog = true }) {
                            Icon(Icons.Default.CreateNewFolder, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("新建文件夹")
                        }
                        if (desktop) {
                            IconButton(onClick = { explorerState.refresh() }, enabled = !ui.isRefreshing) {
                                Icon(Icons.Default.Refresh, "刷新")
                            }
                        }
                        IconButton(onClick = { showLogoutConfirm = true }) {
                            Icon(Icons.AutoMirrored.Filled.Logout, "退出登录")
                        }
                    },
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = pickFile,
                    shape = CircleShape,
                ) {
                    Icon(Icons.Default.UploadFile, "上传文件")
                }
            },
        ) { padding ->
            PullToRefreshBox(
                isRefreshing = ui.isRefreshing,
                onRefresh = explorerState::refresh,
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                Box(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize()) {
                // 上传进度条
                if (ui.uploadProgress != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text(
                                "正在上传（${ui.uploadBatchDone}/${ui.uploadBatchTotal}）：${ui.uploadingName}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Spacer(Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { ui.uploadProgress ?: 0f },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                if (ui.uploadQueue.isNotEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("上传队列", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                                TextButton(onClick = explorerState::clearUploadQueue) { Text("清空") }
                            }
                            ui.uploadQueue.takeLast(5).forEach { item ->
                                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        val statusText = when (item.status) {
                                            UploadItemStatus.PENDING -> "排队中"
                                            UploadItemStatus.UPLOADING -> "上传中"
                                            UploadItemStatus.SUCCESS -> "成功"
                                            UploadItemStatus.FAILED -> "失败"
                                        }
                                        Text(
                                            text = "${item.name} (${formatBytes(item.sizeBytes.toLong())})",
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                        )
                                        val pct = if (item.totalBytes > 0L) {
                                            ((item.sentBytes * 100L) / item.totalBytes).coerceIn(0L, 100L)
                                        } else {
                                            (((item.progress ?: 0f) * 100).toLong()).coerceIn(0L, 100L)
                                        }
                                        Text(
                                            text = statusText,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = when (item.status) {
                                                UploadItemStatus.SUCCESS -> MaterialTheme.colorScheme.primary
                                                UploadItemStatus.FAILED -> MaterialTheme.colorScheme.error
                                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                        )
                                        if (item.status == UploadItemStatus.FAILED) {
                                            Spacer(Modifier.width(6.dp))
                                            TextButton(onClick = { explorerState.retryUpload(item.id) }) { Text("重试") }
                                        } else {
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                text = "${pct}%",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(2.dp))
                                    when (item.status) {
                                        UploadItemStatus.UPLOADING ->
                                            LinearProgressIndicator(
                                                progress = { item.progress ?: 0f },
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                        UploadItemStatus.SUCCESS ->
                                            LinearProgressIndicator(progress = { 1f }, modifier = Modifier.fillMaxWidth())
                                        UploadItemStatus.FAILED ->
                                            LinearProgressIndicator(
                                                progress = { 1f },
                                                color = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                        UploadItemStatus.PENDING ->
                                            LinearProgressIndicator(
                                                progress = { 0f },
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                    }
                                    if (item.status == UploadItemStatus.UPLOADING) {
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text = "速度：${formatBytes(item.speedBytesPerSec)}/s",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 文件列表
                when {
                    ui.files.isEmpty() -> {
                        Box(
                            Modifier.fillMaxSize().padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                if (ui.isRefreshing) {
                                    Text(
                                        "加载中...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.Cloud,
                                        contentDescription = null,
                                        modifier = Modifier.size(64.dp),
                                        tint = MaterialTheme.colorScheme.outlineVariant,
                                    )
                                    Spacer(Modifier.height(16.dp))
                                    Text(
                                        "此目录为空",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        if (desktop) "点击右下角上传，或直接拖拽文件到窗口上传" else "点击右下角上传按钮添加文件",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        }
                    }

                    else -> {
                        LazyColumn(Modifier.weight(1f)) {
                            items(ui.files, key = { it.id }) { file ->
                                FileListItem(
                                    file = file,
                                    compactActions = !desktop,
                                    selected = selectedFileIds.contains(file.id),
                                    onToggleSelected = { checked ->
                                        selectedFileIds = if (checked) {
                                            selectedFileIds + file.id
                                        } else {
                                            selectedFileIds - file.id
                                        }
                                    },
                                    onOpen = {
                                        if (file.isDir) explorerState.navigateTo(file.path)
                                        else fileDetailTarget = file
                                    },
                                    onDownload = { explorerState.downloadFile(file) },
                                    onShare = { explorerState.initiateShare(file) },
                                    onRename = {
                                        renameTarget = file
                                        renameText = file.fileName
                                    },
                                    onMove = if (!file.isDir) {
                                        {
                                            moveTargets = listOf(file)
                                            movePickerPrefix = ui.currentPrefix
                                        }
                                    } else {
                                        null
                                    },
                                    onDelete = { deleteTargets = listOf(file) },
                                )
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 56.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                )
                            }
                        }
                    }
                }
            }
                    if (desktop && isDesktopDragOver) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize().padding(24.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(18.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    tonalElevation = 10.dp,
                                    shadowElevation = 8.dp,
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 20.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.UploadFile,
                                            contentDescription = null,
                                            modifier = Modifier.size(36.dp),
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                        Spacer(Modifier.height(10.dp))
                                        Text(
                                            text = "松开即可上传",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = "支持一次拖拽多个文件",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (ui.isDeleting) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.22f),
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    tonalElevation = 6.dp,
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.5.dp)
                                        Spacer(Modifier.width(12.dp))
                                        Text("正在删除，请稍候…")
                                    }
                                }
                            }
                        }
                    }
                    if (ui.isCreatingFolder) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.2f),
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    tonalElevation = 6.dp,
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.5.dp)
                                        Spacer(Modifier.width(12.dp))
                                        Text("正在创建文件夹：${ui.creatingFolderName ?: ""}")
                                    }
                                }
                            }
                        }
                    }
                    if (ui.isMoving) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.2f),
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    tonalElevation = 6.dp,
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.5.dp)
                                        Spacer(Modifier.width(12.dp))
                                        Text("正在移动文件…")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 重命名对话框
        renameTarget?.let { target ->
            AlertDialog(
                onDismissRequest = { renameTarget = null },
                title = { Text("重命名") },
                text = {
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        label = { Text("新名称") },
                        singleLine = true,
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (renameText.isNotBlank()) {
                                explorerState.renameFile(target, renameText.trim())
                                renameTarget = null
                            }
                        },
                        enabled = renameText.isNotBlank(),
                    ) { Text("确定") }
                },
                dismissButton = {
                    TextButton(onClick = { renameTarget = null }) { Text("取消") }
                },
            )
        }

        if (moveTargets.isNotEmpty()) {
            val destNorm = movePickerPrefix.trim().let { p ->
                when {
                    p.isEmpty() -> ""
                    p.endsWith("/") -> p
                    else -> "$p/"
                }
            }
            val canConfirmMove = moveTargets.any { f ->
                !f.isDir && (destNorm + f.fileName) != f.path
            }
            val folders = explorerState.foldersForMovePicker(movePickerPrefix)
            AlertDialog(
                onDismissRequest = { moveTargets = emptyList() },
                title = { Text("移动到文件夹") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "将 ${moveTargets.size} 个文件移入所选路径。",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = "目标：${
                                if (movePickerPrefix.isEmpty()) {
                                    "根目录"
                                } else {
                                    "/${movePickerPrefix.trimEnd('/')}"
                                }
                            }",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        TextButton(
                            onClick = {
                                movePickerPrefix = explorerState.parentOfDirPrefix(movePickerPrefix)
                            },
                            enabled = movePickerPrefix.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("上级目录")
                        }
                        Text(
                            "点击文件夹进入子目录；在目标位置点「移到这里」。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            items(folders, key = { it.path }) { folder ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                                        .clickable { movePickerPrefix = folder.path }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        folder.fileName,
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                        if (folders.isEmpty()) {
                            Text(
                                "当前路径下没有子文件夹；可将文件直接移入当前目录，或先返回上级再进入其他文件夹。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            explorerState.moveFilesToFolder(moveTargets, movePickerPrefix)
                            moveTargets = emptyList()
                            selectedFileIds = emptySet()
                        },
                        enabled = canConfirmMove,
                    ) { Text("移到这里") }
                },
                dismissButton = {
                    TextButton(onClick = { moveTargets = emptyList() }) { Text("取消") }
                },
            )
        }

        if (showCreateFolderDialog) {
            val createFocusRequester = remember { FocusRequester() }
            AlertDialog(
                onDismissRequest = { showCreateFolderDialog = false },
                title = { Text("新建文件夹") },
                text = {
                    OutlinedTextField(
                        value = newFolderName,
                        onValueChange = { newFolderName = it },
                        label = { Text("文件夹名称") },
                        singleLine = true,
                        modifier = Modifier.focusRequester(createFocusRequester),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (newFolderName.isNotBlank()) {
                                    explorerState.createFolder(newFolderName.trim())
                                    showCreateFolderDialog = false
                                    newFolderName = ""
                                }
                            },
                        ),
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            explorerState.createFolder(newFolderName.trim())
                            showCreateFolderDialog = false
                            newFolderName = ""
                        },
                        enabled = newFolderName.isNotBlank() && !ui.isCreatingFolder,
                    ) { Text(if (ui.isCreatingFolder) "创建中..." else "创建") }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showCreateFolderDialog = false
                            newFolderName = ""
                        },
                    ) { Text("取消") }
                },
            )
            LaunchedEffect(Unit) {
                createFocusRequester.requestFocus()
            }
        }

        if (deleteTargets.isNotEmpty()) {
            val deletingNames = deleteTargets.take(3).joinToString("、") { it.fileName }
            val extraDeleting = deleteTargets.size - 3
            AlertDialog(
                onDismissRequest = { deleteTargets = emptyList() },
                title = { Text("确认删除") },
                text = {
                    Text(
                        if (extraDeleting > 0) {
                            "确定删除 ${deleteTargets.size} 项：$deletingNames 等 $extraDeleting 项吗？删除后无法恢复。"
                        } else {
                            "确定删除：$deletingNames 吗？删除后无法恢复。"
                        },
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            deleteTargets.forEach { explorerState.deleteFile(it) }
                            selectedFileIds = selectedFileIds - deleteTargets.map { it.id }.toSet()
                            deleteTargets = emptyList()
                        }
                    ) { Text("删除", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { deleteTargets = emptyList() }) { Text("取消") }
                },
            )
        }

        if (showLogoutConfirm) {
            AlertDialog(
                onDismissRequest = { showLogoutConfirm = false },
                title = { Text("退出登录") },
                text = { Text("确定要退出当前账号吗？") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showLogoutConfirm = false
                            gate.signOut {}
                        }
                    ) { Text("退出", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { showLogoutConfirm = false }) { Text("取消") }
                },
            )
        }

        if (pendingDroppedFiles.isNotEmpty()) {
            val previewNames = pendingDroppedFiles.take(3).joinToString("、") { it.name }
            val extra = pendingDroppedFiles.size - 3
            AlertDialog(
                onDismissRequest = { pendingDroppedFiles = emptyList() },
                title = { Text("确认上传") },
                text = {
                    Text(
                        if (extra > 0) {
                            "将上传 ${pendingDroppedFiles.size} 个文件：$previewNames 等 $extra 个。是否继续？"
                        } else {
                            "将上传 ${pendingDroppedFiles.size} 个文件：$previewNames。是否继续？"
                        },
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            explorerState.uploadFiles(pendingDroppedFiles)
                            pendingDroppedFiles = emptyList()
                        },
                    ) { Text("开始上传") }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDroppedFiles = emptyList() }) { Text("取消") }
                },
            )
        }

        if (pendingSharedFiles.isNotEmpty()) {
            val previewNames = pendingSharedFiles.take(3).joinToString("、") { it.name }
            val extra = pendingSharedFiles.size - 3
            AlertDialog(
                onDismissRequest = { pendingSharedFiles = emptyList() },
                title = { Text("保存到 Cloud") },
                text = {
                    Text(
                        if (extra > 0) {
                            "接收到 ${pendingSharedFiles.size} 个系统分享文件：$previewNames 等 $extra 个。是否开始上传到云盘？"
                        } else {
                            "接收到 ${pendingSharedFiles.size} 个系统分享文件：$previewNames。是否开始上传到云盘？"
                        },
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            explorerState.uploadFiles(pendingSharedFiles)
                            pendingSharedFiles = emptyList()
                        },
                    ) { Text("开始上传") }
                },
                dismissButton = {
                    TextButton(onClick = { pendingSharedFiles = emptyList() }) { Text("取消") }
                },
            )
        }

        fileDetailTarget?.let { file ->
            AlertDialog(
                onDismissRequest = { fileDetailTarget = null },
                title = { Text("文件详情") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("名称：${file.fileName}")
                        Text("路径：${file.path}")
                        Text("大小：${formatBytes(file.sizeBytes)}")
                        Text("类型：${file.mimeType ?: "未知"}")
                        Text("更新时间：${formatUpdatedAt(file.updatedAtMs)}")
                    }
                },
                confirmButton = {
                    TextButton(onClick = { fileDetailTarget = null }) { Text("关闭") }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            fileDetailTarget = null
                            explorerState.downloadFile(file)
                        }
                    ) { Text("下载") }
                },
            )
        }

        // 分享底部弹窗
        if (ui.shareStep != ShareStep.HIDDEN) {
            ShareBottomSheet(
                ui = ui,
                explorerState = explorerState,
                onDismiss = explorerState::dismissShare,
            )
        }
    }
}

@Composable
private fun FileListItem(
    file: CloudFileItem,
    compactActions: Boolean,
    selected: Boolean,
    onToggleSelected: (Boolean) -> Unit,
    onOpen: () -> Unit,
    onDownload: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onMove: (() -> Unit)?,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = selected,
            onCheckedChange = { onToggleSelected(it) },
        )
        Spacer(Modifier.width(6.dp))
        Icon(
            imageVector = if (file.isDir) Icons.Default.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
            contentDescription = null,
            tint = if (file.isDir) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(36.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = file.fileName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (file.isDir) "文件夹" else formatBytes(file.sizeBytes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (compactActions) {
            var menuExpanded by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, "更多操作")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("重命名") },
                        onClick = {
                            menuExpanded = false
                            onRename()
                        },
                        leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null) },
                    )
                    if (!file.isDir && onMove != null) {
                        DropdownMenuItem(
                            text = { Text("移动到…") },
                            onClick = {
                                menuExpanded = false
                                onMove()
                            },
                            leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
                        )
                    }
                    if (!file.isDir) {
                        DropdownMenuItem(
                            text = { Text("下载") },
                            onClick = {
                                menuExpanded = false
                                onDownload()
                            },
                            leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
                        )
                        DropdownMenuItem(
                            text = { Text("分享") },
                            onClick = {
                                menuExpanded = false
                                onShare()
                            },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                    )
                }
            }
        } else {
            if (!file.isDir) {
                IconButton(onClick = onDownload) {
                    Icon(Icons.Default.Download, "下载", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onShare) {
                    Icon(Icons.Default.Share, "分享", tint = MaterialTheme.colorScheme.secondary)
                }
            }
            IconButton(onClick = onRename) {
                Icon(Icons.Default.DriveFileRenameOutline, "重命名")
            }
            if (!file.isDir && onMove != null) {
                IconButton(onClick = onMove) {
                    Icon(Icons.Default.Folder, "移动到文件夹", tint = MaterialTheme.colorScheme.tertiary)
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, "删除", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var v = bytes.toDouble()
    var u = 0
    while (v >= 1024 && u < units.lastIndex) {
        v /= 1024
        u++
    }
    return if (u == 0) "$bytes ${units[u]}" else "%.1f %s".format(v, units[u])
}

private fun formatUpdatedAt(epochMs: Long): String {
    if (epochMs <= 0L) return "未知"
    val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
    val diff = (now - epochMs).coerceAtLeast(0L)
    val sec = diff / 1000L
    val min = sec / 60L
    val hour = min / 60L
    val day = hour / 24L
    val relative = when {
        sec < 10L -> "刚刚"
        sec < 60L -> "${sec} 秒前"
        min < 60L -> "${min} 分钟前"
        hour < 24L -> "${hour} 小时前"
        day < 30L -> "${day} 天前"
        else -> "较久以前"
    }
    return "$relative（$epochMs）"
}

// ═══════════════════════════════════════════════════════════════════════════════
//  分享底部弹窗
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareBottomSheet(
    ui: cn.verlu.cloud.presentation.files.FileExplorerUiState,
    explorerState: FileExplorerState,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val clipboardManager = LocalClipboardManager.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        when (ui.shareStep) {
            ShareStep.OPTIONS -> ShareOptionsContent(
                fileName = ui.shareTarget?.fileName ?: "",
                fileSize = ui.shareTarget?.sizeBytes ?: 0L,
                shareUrl = ui.shareUrl,
                isGenerating = ui.isGeneratingShareUrl,
                shareError = ui.shareError,
                selectedExpirySeconds = ui.shareExpiresInSeconds,
                expiryOptions = ui.shareExpiryOptions,
                expiryLabel = explorerState.formatExpiryLabel(ui.shareExpiresInSeconds),
                onCopyLink = { url ->
                    clipboardManager.setText(AnnotatedString(url))
                },
                onSelectExpiry = explorerState::setShareExpiry,
                onShareToFriend = { explorerState.openFriendPicker() },
                onDismissError = explorerState::clearShareError,
                onDismiss = onDismiss,
            )
            ShareStep.FRIEND_PICKER -> FriendPickerContent(
                friends = ui.friends,
                isLoading = ui.isLoadingFriends,
                isSending = ui.isSendingShare,
                shareError = ui.shareError,
                onFriendSelect = { friend -> explorerState.sendShareToFriend(friend) },
                onBack = { explorerState.backToShareOptions() },
                onDismissError = explorerState::clearShareError,
            )
            ShareStep.SUCCESS -> ShareSuccessContent(
                friend = ui.shareSentToFriend,
                onDone = onDismiss,
            )
            else -> {}
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ─── 分享选项页 ───────────────────────────────────────────────────────────────

@Composable
private fun ShareOptionsContent(
    fileName: String,
    fileSize: Long,
    shareUrl: String?,
    isGenerating: Boolean,
    shareError: String?,
    selectedExpirySeconds: Int,
    expiryOptions: List<Int>,
    expiryLabel: String,
    onCopyLink: (String) -> Unit,
    onSelectExpiry: (Int) -> Unit,
    onShareToFriend: () -> Unit,
    onDismissError: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
    ) {
        // 标题区
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Share,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("分享文件", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = formatBytes(fileSize),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, contentDescription = "关闭", modifier = Modifier.size(18.dp))
            }
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = "链接有效期：$expiryLabel",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            expiryOptions.forEach { sec ->
                val selected = sec == selectedExpirySeconds
                FilterChip(
                    selected = selected,
                    onClick = { onSelectExpiry(sec) },
                    label = {
                        Text(
                            when (sec) {
                                1800 -> "30分钟"
                                3600 -> "1小时"
                                86400 -> "24小时"
                                604800 -> "7天"
                                else -> "${sec}s"
                            }
                        )
                    },
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // 链接卡片
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(14.dp)) {
                Text(
                    text = "分享链接",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                if (isGenerating) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text(
                            "正在生成链接…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else if (shareUrl != null) {
                    Text(
                        text = shareUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { onCopyLink(shareUrl) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("复制链接")
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // 分享给好友按钮
        OutlinedButton(
            onClick = onShareToFriend,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            enabled = shareUrl != null && !isGenerating,
        ) {
            Icon(Icons.Default.People, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("发给好友（Talk）")
        }

        // 错误提示
        if (shareError != null) {
            Spacer(Modifier.height(10.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                shape = RoundedCornerShape(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.ErrorOutline, null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = shareError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismissError, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, null, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

// ─── 好友选择页 ───────────────────────────────────────────────────────────────

@Composable
private fun FriendPickerContent(
    friends: List<CloudFriendItem>,
    isLoading: Boolean,
    isSending: Boolean,
    shareError: String?,
    onFriendSelect: (CloudFriendItem) -> Unit,
    onBack: () -> Unit,
    onDismissError: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        // 标题行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(
                "选择好友",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        HorizontalDivider()

        // 错误条
        if (shareError != null) {
            Surface(color = MaterialTheme.colorScheme.errorContainer) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = shareError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismissError, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, null, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }

        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text("加载好友列表…", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            friends.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.PeopleAlt, null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.outlineVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "暂无好友，请先在 Talk 中添加好友",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp),
                        )
                    }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp),
                ) {
                    items(friends, key = { it.userId }) { friend ->
                        FriendPickerItem(
                            friend = friend,
                            isSending = isSending,
                            onClick = { onFriendSelect(friend) },
                        )
                        HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun FriendPickerItem(
    friend: CloudFriendItem,
    isSending: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = friend.roomId != null && !isSending, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 头像（文字占位）
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = friend.displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = friend.displayName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (friend.roomId == null) {
                Text(
                    text = "暂无聊天室，无法发送",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                friend.email?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (friend.roomId != null) {
            if (isSending) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    Icons.Default.Send,
                    contentDescription = "发送给 ${friend.displayName}",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

// ─── 分享成功页 ───────────────────────────────────────────────────────────────

@Composable
private fun ShareSuccessContent(
    friend: CloudFriendItem?,
    onDone: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(8.dp))
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "分享成功",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        if (friend != null) {
            Text(
                text = "已将文件链接发送给 ${friend.displayName}，对方可在 Talk 中查看",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text("完成")
        }
    }
}
