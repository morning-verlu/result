package cn.verlu.cnchess.presentation.auth.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.verlu.cnchess.R
import cn.verlu.cnchess.presentation.auth.tryLaunchSyncSsoAuthorize
import cn.verlu.cnchess.presentation.auth.vm.AuthFormViewModel
import cn.verlu.cnchess.presentation.auth.vm.AuthMode
import cn.verlu.cnchess.presentation.navigation.LocalSnackbarHostState
import kotlinx.coroutines.launch

@Composable
fun AuthRoute(
    modifier: Modifier = Modifier,
    viewModel: AuthFormViewModel = hiltViewModel(),
    onOpenEmailLogin: () -> Unit,
    onOpenEmailRegister: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    var pendingSyncAuthorize by remember { mutableStateOf(false) }

    LaunchedEffect(state.qrSessionId, pendingSyncAuthorize, state.error) {
        if (state.error != null) {
            pendingSyncAuthorize = false
            return@LaunchedEffect
        }
        if (!pendingSyncAuthorize) return@LaunchedEffect
        val sid = state.qrSessionId ?: return@LaunchedEffect
        pendingSyncAuthorize = false
        val ok = tryLaunchSyncSsoAuthorize(context, sid)
        if (!ok) {
            scope.launch {
                snackbar.showSnackbar("请先安装 Sync 应用")
            }
            viewModel.cancelSyncQrLogin()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.checkQrSessionStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val appIconPainter = remember(context.packageName) {
        val drawable = context.packageManager.getApplicationIcon(context.packageName)
        BitmapPainter(drawable.toBitmap(256, 256).asImageBitmap())
    }

    Box(modifier = modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(48.dp))

                Image(
                    painter = appIconPainter,
                    contentDescription = "应用图标",
                    modifier = Modifier.size(80.dp),
                )

                Spacer(Modifier.height(32.dp))

                Text(
                    text = "CN Chess",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(Modifier.height(48.dp))

                Button(
                    onClick = {
                        viewModel.setMode(AuthMode.Login)
                        onOpenEmailLogin()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    enabled = !state.isSubmitting,
                ) {
                    Icon(
                        Icons.Default.Email,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "使用邮箱登录",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Spacer(Modifier.height(16.dp))

                OutlinedButton(
                    onClick = {
                        viewModel.setMode(AuthMode.Register)
                        onOpenEmailRegister()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    enabled = !state.isSubmitting,
                ) {
                    Text(text = "注册新账号", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
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
                        text = "或",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }

                Spacer(Modifier.height(32.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = { viewModel.signInWithGithub() },
                        modifier = Modifier.size(48.dp),
                        enabled = !state.isSubmitting,
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_github),
                            contentDescription = "使用 GitHub 登录",
                            modifier = Modifier.size(40.dp),
                        )
                    }

                    Spacer(Modifier.width(32.dp))

                    IconButton(
                        onClick = { viewModel.signInWithGoogle() },
                        modifier = Modifier.size(48.dp),
                        enabled = !state.isSubmitting,
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_google),
                            contentDescription = "使用 Google 登录",
                            modifier = Modifier.size(40.dp),
                        )
                    }

                    Spacer(Modifier.width(32.dp))

                    IconButton(
                        onClick = {
                            pendingSyncAuthorize = true
                            viewModel.beginSyncQrLogin()
                        },
                        modifier = Modifier.size(48.dp),
                        enabled = !state.isSubmitting,
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_sync),
                            contentDescription = "使用 Sync 授权登录",
                            modifier = Modifier.size(40.dp),
                        )
                    }
                }

                state.error?.let {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        AuthSessionLoadingOverlay(alsoWhen = state.isSubmitting)
    }
}
