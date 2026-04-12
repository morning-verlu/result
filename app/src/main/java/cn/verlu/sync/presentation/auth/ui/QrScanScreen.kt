package cn.verlu.sync.presentation.auth.ui

import android.Manifest
import android.util.Log
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "QrScanScreen"

@Composable
fun QrScanScreen(
    modifier: Modifier = Modifier,
    onScanResult: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember { mutableStateOf(false) }

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        val checkResult = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
        if (checkResult == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            hasCameraPermission = true
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Box(modifier = modifier) {
        if (hasCameraPermission) {
            CameraPreview(
                modifier = Modifier.fillMaxSize(),
                onScanResult = onScanResult
            )
            ScanOverlay(modifier = Modifier.fillMaxSize())
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "需要相机权限才能扫码",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        Text(
            text = "请扫描其他应用显示的二维码",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 64.dp)
        )
    }
}

@Composable
private fun CameraPreview(
    modifier: Modifier = Modifier,
    onScanResult: (String) -> Unit
) {
    val context = LocalContext.current
    // 必须用 Composition 的 LifecycleOwner；后台线程读 mutableStateOf 在 release 下不稳定。
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanned = remember { AtomicBoolean(false) }
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
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
                            processImageProxy(
                                barcodeScanner,
                                imageProxy,
                                mainExecutor,
                                onScanResult,
                                scanned
                            )
                        }
                    }

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalyzer
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to bind camera use cases", e)
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        onRelease = { previewView ->
            try {
                val cameraProviderFuture = ProcessCameraProvider.getInstance(previewView.context)
                cameraProviderFuture.get().unbindAll()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to unbind camera on release", e)
            }
        },
        modifier = modifier.clipToBounds()
    )
}

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
private fun processImageProxy(
    barcodeScanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    imageProxy: ImageProxy,
    mainExecutor: Executor,
    onResult: (String) -> Unit,
    scanned: AtomicBoolean
) {
    val mediaImage = imageProxy.image ?: run { imageProxy.close(); return }
    val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

    barcodeScanner.process(inputImage)
        .addOnSuccessListener { barcodes ->
            val rawValue = barcodes.firstOrNull { it.format == Barcode.FORMAT_QR_CODE }?.rawValue
            if (rawValue != null && rawValue.startsWith("verlusync://authorize")) {
                val uri = android.net.Uri.parse(rawValue)
                val sessionId = uri.getQueryParameter("sessionId")
                if (!sessionId.isNullOrBlank() && scanned.compareAndSet(false, true)) {
                    mainExecutor.execute { onResult(sessionId) }
                }
            }
        }
        .addOnFailureListener { /* 仅关闭帧，避免异常路径泄漏 */ }
        .addOnCompleteListener { imageProxy.close() }
}

@Composable
private fun ScanOverlay(modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier.graphicsLayer {
            compositingStrategy = CompositingStrategy.Offscreen
        }
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val boxSize = canvasWidth * 0.65f
        val left = (canvasWidth - boxSize) / 2f
        val top = (canvasHeight - boxSize) / 2.2f

        // Dim background
        drawRect(color = Color(0x99000000))

        // Clear the scan box (punch-through)
        drawRoundRect(
            color = Color.Transparent,
            topLeft = Offset(left, top),
            size = Size(boxSize, boxSize),
            cornerRadius = CornerRadius(24.dp.toPx()),
            blendMode = BlendMode.Clear
        )

        // Border around scan box
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(left, top),
            size = Size(boxSize, boxSize),
            cornerRadius = CornerRadius(24.dp.toPx()),
            style = Stroke(width = 3.dp.toPx())
        )

        // Corner accents (top-left)
        val cornerLength = 40.dp.toPx()
        val strokeWidth = 6.dp.toPx()
        val accentColor = Color(0xFF4CAF98)
        val corners = listOf(
            // TL
            Triple(Offset(left, top + cornerLength), Offset(left, top), Offset(left + cornerLength, top)),
            // TR
            Triple(Offset(left + boxSize - cornerLength, top), Offset(left + boxSize, top), Offset(left + boxSize, top + cornerLength)),
            // BL
            Triple(Offset(left, top + boxSize - cornerLength), Offset(left, top + boxSize), Offset(left + cornerLength, top + boxSize)),
            // BR
            Triple(Offset(left + boxSize - cornerLength, top + boxSize), Offset(left + boxSize, top + boxSize), Offset(left + boxSize, top + boxSize - cornerLength))
        )

        corners.forEach { (start, mid, end) ->
            drawLine(color = accentColor, start = start, end = mid, strokeWidth = strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            drawLine(color = accentColor, start = mid, end = end, strokeWidth = strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        }
    }
}
