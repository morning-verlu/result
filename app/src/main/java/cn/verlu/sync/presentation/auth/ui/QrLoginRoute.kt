package cn.verlu.sync.presentation.auth.ui

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.verlu.sync.presentation.auth.vm.QrLoginState
import cn.verlu.sync.presentation.auth.vm.QrLoginViewModel
import cn.verlu.sync.presentation.ui.SyncLoadingIndicator

import android.app.Activity
import android.content.Intent
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun QrLoginRoute(
    modifier: Modifier = Modifier,
    sessionId: String? = null,
    /** 授权成功后自动回到该包名的 App（显式包名，不出现选择器）。null 则不跳转。 */
    returnToPackage: String? = null,
    viewModel: QrLoginViewModel = hiltViewModel(
        key = "qr_${sessionId.orEmpty()}_${returnToPackage.orEmpty()}",
    ),
    onFinished: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // 必须先于子节点 Success 里的 LaunchedEffect：弹出路由时立刻清状态，否则同一 ViewModel
    // 次日再进页面仍会短暂处于 Success，重复拉起 Talk。
    DisposableEffect(Unit) {
        onDispose { viewModel.cancel() }
    }

    LaunchedEffect(sessionId) {
        if (!sessionId.isNullOrBlank()) {
            viewModel.setSessionId(sessionId)
        } else {
            viewModel.cancel()
        }
    }
    AnimatedContent(targetState = uiState, modifier = modifier, label = "qr_anim") { state ->
        when (state) {
            is QrLoginState.Scanning -> {
                QrScanScreen(
                    modifier = Modifier.fillMaxSize(),
                    onScanResult = viewModel::onScanResult
                )
            }

            is QrLoginState.Confirming -> {
                AuthorizeLoginScreen(
                    modifier = modifier,
                    sessionId = state.sessionId,
                    onApprove = { viewModel.approveLogin(state.sessionId) },
                    onReject = viewModel::cancel
                )
            }

            is QrLoginState.Loading -> {
                Box(modifier = modifier, contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        SyncLoadingIndicator(modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(24.dp))
                        Text(
                            text = "正在授权登录...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }

            is QrLoginState.Success -> {
                val context = LocalContext.current
                LaunchedEffect(state) {
                    if (!viewModel.consumeSuccessAutoNavIfNeeded()) return@LaunchedEffect
                    if (returnToPackage != null) {
                        context.packageManager.getLaunchIntentForPackage(returnToPackage)?.let { launch ->
                            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            runCatching { context.startActivity(launch) }
                        }
                        (context as? Activity)?.finish()
                    } else {
                        onFinished()
                    }
                }
                Box(modifier = modifier, contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(80.dp),
                            tint = Color(0xFF4CAF50)
                        )
                        Spacer(Modifier.height(24.dp))
                        Text(
                            text = "授权成功！",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "对方设备已成功登录",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(48.dp))
                        Button(
                            onClick = {
                                viewModel.consumeSuccessAutoNavIfNeeded()
                                if (returnToPackage != null) {
                                    context.packageManager.getLaunchIntentForPackage(returnToPackage)?.let { launch ->
                                        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                        runCatching { context.startActivity(launch) }
                                    }
                                    (context as? Activity)?.finish()
                                } else {
                                    onFinished()
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("返回")
                        }
                    }
                }
            }

            is QrLoginState.Error -> {
                Box(modifier = modifier, contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            modifier = Modifier.size(80.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(24.dp))
                        Text(
                            text = "授权失败",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(48.dp))
                        Button(
                            onClick = viewModel::cancel,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("重试")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AuthorizeLoginScreen(
    modifier: Modifier = Modifier,
    sessionId: String,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.QrCodeScanner,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(Modifier.height(32.dp))

            Text(
                text = "是否授权登录？",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "另一台设备正在请求用你的账号登录",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "会话 ID：${sessionId.take(8)}...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(Modifier.height(48.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedButton(
                    onClick = onReject,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("拒绝")
                }
                Button(
                    onClick = onApprove,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("确认授权", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
