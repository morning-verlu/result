package cn.verlu.talk.presentation.contacts

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.verlu.talk.presentation.navigation.LocalSnackbarHostState
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.launch
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "QrScanFriendScreen"
private const val TALK_UID_SCHEME = "verluTalk://user?uid="

@Composable
fun QrScanFriendScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    viewModel: QrScanFriendViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar: SnackbarHostState = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val view = LocalView.current

    var hasCameraPermission by remember { mutableStateOf(false) }
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        hasCameraPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
    }

    // 错误在扫码页提示（profile 未找到等），然后重置以允许重试
    LaunchedEffect(state.error) {
        val err = state.error ?: return@LaunchedEffect
        scope.launch {
            snackbar.showSnackbar(err)
            viewModel.clearError()
        }
    }

    // 找到 profile → 震动 + 立刻返回主页，主页负责展示底部 sheet
    LaunchedEffect(state.scannedProfile) {
        if (state.scannedProfile == null) return@LaunchedEffect
        ViewCompat.performHapticFeedback(view, HapticFeedbackConstantsCompat.KEYBOARD_TAP)
        onBack()
    }

    // Cloud 授权成功 → 震动 + 返回，并弹出 snackbar
    LaunchedEffect(state.cloudLoginApproved) {
        if (!state.cloudLoginApproved) return@LaunchedEffect
        ViewCompat.performHapticFeedback(view, HapticFeedbackConstantsCompat.KEYBOARD_TAP)
        viewModel.clearCloudLoginApproved()
        scope.launch { snackbar.showSnackbar("已授权 Cloud 桌面端登录") }
        onBack()
    }

    // 相机暂停：正在查找、已找到 profile（即将 pop）、等待 Cloud 授权确认
    val cameraPaused = state.isLookingUp || state.scannedProfile != null ||
        state.cloudLoginSessionId != null || state.isApprovingCloud

    // Cloud 授权确认对话框
    if (state.cloudLoginSessionId != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissCloudLogin() },
            title = { Text("授权 Cloud 登录") },
            text = {
                Column {
                    Text(
                        "另一台设备上的 Cloud 应用正在请求使用你的账号登录。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "会话：${state.cloudLoginSessionId!!.take(8)}...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // 授权失败错误直接显示在 Dialog 内，不走 snackbar（避免被 Dialog 层遮挡）
                    if (state.cloudLoginError != null) {
                        Spacer(Modifier.height(10.dp))
                        androidx.compose.material3.Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                        ) {
                            Text(
                                text = state.cloudLoginError!!,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.approveCloudLogin() },
                    enabled = !state.isApprovingCloud,
                ) {
                    if (state.isApprovingCloud) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (state.cloudLoginError != null) "重试" else "确认授权")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissCloudLogin() }) {
                    Text("取消")
                }
            },
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (hasCameraPermission) {
            FriendCameraPreview(
                modifier = Modifier.fillMaxSize(),
                onScanResult = { raw ->
                    when {
                        raw.startsWith(TALK_UID_SCHEME) -> {
                            val uid = raw.removePrefix(TALK_UID_SCHEME).trim()
                            if (uid.isNotBlank()) viewModel.onQrScanned(uid)
                        }
                        raw.startsWith("verlusync://authorize_sso") -> {
                            val sessionId = Uri.parse(raw).getQueryParameter("sessionId")
                            if (!sessionId.isNullOrBlank()) viewModel.onCloudLoginQrScanned(sessionId)
                        }
                    }
                },
                paused = cameraPaused,
            )
            ScanOverlayFriend(modifier = Modifier.fillMaxSize())
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "扫码需要相机权限",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("允许使用相机")
                }
            }
        }

        // 提示文字
        if (hasCameraPermission && !cameraPaused) {
            Text(
                text = "扫描好友的「我的二维码」或 Cloud 桌面端的登录码",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 120.dp)
                    .padding(horizontal = 32.dp),
            )
        }

        // 查找中 spinner
        if (state.isLookingUp) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 80.dp)
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        RoundedCornerShape(12.dp),
                    )
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "正在识别…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun FriendCameraPreview(
    modifier: Modifier = Modifier,
    onScanResult: (String) -> Unit,
    paused: Boolean,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanned = remember { AtomicBoolean(false) }
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }

    LaunchedEffect(paused) {
        if (!paused) scanned.set(false)
    }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }

            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build()
                    .also { it.setSurfaceProvider(previewView.surfaceProvider) }

                val barcodeScanner = BarcodeScanning.getClient()
                val imageAnalyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(executor) { imageProxy ->
                            if (scanned.get()) {
                                imageProxy.close()
                                return@setAnalyzer
                            }
                            processFriendQrProxy(barcodeScanner, imageProxy, mainExecutor, scanned) { rawValue ->
                                onScanResult(rawValue)
                            }
                        }
                    }

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalyzer)
                } catch (e: Exception) {
                    Log.e(TAG, "bindToLifecycle failed", e)
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        onRelease = { pv ->
            try {
                ProcessCameraProvider.getInstance(pv.context).get().unbindAll()
            } catch (e: Exception) {
                Log.e(TAG, "unbind failed", e)
            }
        },
        modifier = modifier.clipToBounds(),
    )
}

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
private fun processFriendQrProxy(
    barcodeScanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    imageProxy: ImageProxy,
    mainExecutor: Executor,
    scanned: AtomicBoolean,
    onResult: (String) -> Unit,
) {
    val mediaImage = imageProxy.image ?: run { imageProxy.close(); return }
    val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
    barcodeScanner.process(inputImage)
        .addOnSuccessListener { barcodes ->
            val rawValue = barcodes.firstOrNull { it.format == Barcode.FORMAT_QR_CODE }?.rawValue
            if (rawValue != null && scanned.compareAndSet(false, true)) {
                mainExecutor.execute { onResult(rawValue) }
            }
        }
        .addOnFailureListener { }
        .addOnCompleteListener { imageProxy.close() }
}

@Composable
private fun ScanOverlayFriend(modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier.graphicsLayer {
            compositingStrategy = CompositingStrategy.Offscreen
        },
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val boxSize = canvasWidth * 0.65f
        val left = (canvasWidth - boxSize) / 2f
        val top = (canvasHeight - boxSize) / 2.2f

        drawRect(color = Color(0x99000000))
        drawRoundRect(
            color = Color.Transparent,
            topLeft = Offset(left, top),
            size = Size(boxSize, boxSize),
            cornerRadius = CornerRadius(24.dp.toPx()),
            blendMode = BlendMode.Clear,
        )
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(left, top),
            size = Size(boxSize, boxSize),
            cornerRadius = CornerRadius(24.dp.toPx()),
            style = Stroke(width = 3.dp.toPx()),
        )
        val cornerLength = 40.dp.toPx()
        val strokeWidth = 6.dp.toPx()
        val accentColor = Color(0xFF4CAF98)
        listOf(
            Triple(Offset(left, top + cornerLength), Offset(left, top), Offset(left + cornerLength, top)),
            Triple(Offset(left + boxSize - cornerLength, top), Offset(left + boxSize, top), Offset(left + boxSize, top + cornerLength)),
            Triple(Offset(left, top + boxSize - cornerLength), Offset(left, top + boxSize), Offset(left + cornerLength, top + boxSize)),
            Triple(Offset(left + boxSize - cornerLength, top + boxSize), Offset(left + boxSize, top + boxSize), Offset(left + boxSize, top + boxSize - cornerLength)),
        ).forEach { (start, mid, end) ->
            drawLine(color = accentColor, start = start, end = mid, strokeWidth = strokeWidth, cap = StrokeCap.Round)
            drawLine(color = accentColor, start = mid, end = end, strokeWidth = strokeWidth, cap = StrokeCap.Round)
        }
    }
}
