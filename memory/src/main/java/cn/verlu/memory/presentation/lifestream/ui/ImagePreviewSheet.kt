package cn.verlu.memory.presentation.lifestream.ui

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.SubcomposeAsyncImage
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImagePreviewSheet(
    imageUrls: List<String>,
    initialIndex: Int,
    onDismiss: () -> Unit,
) {
    if (imageUrls.isEmpty()) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, imageUrls.lastIndex),
        pageCount = { imageUrls.size },
    )

    var chromeVisible by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var savedUrls by remember { mutableStateOf(setOf<String>()) }
    val currentUrl = imageUrls.getOrElse(pagerState.currentPage) { imageUrls.first() }
    val saveDone = currentUrl in savedUrls

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                ZoomableImagePage(
                    imageUrl = imageUrls[page],
                    onTap = { chromeVisible = !chromeVisible },
                    onDismiss = onDismiss,
                )
            }

            AnimatedVisibility(
                visible = chromeVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(WindowInsets.systemBars.asPaddingValues())
                        .padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    SavePillButton(
                        saved = saveDone,
                        loading = isSaving,
                        onClick = {
                            if (isSaving || saveDone) return@SavePillButton
                            isSaving = true
                            scope.launch {
                                val ok = saveImageToGallery(context, currentUrl)
                                isSaving = false
                                if (ok) savedUrls = savedUrls + currentUrl
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ZoomableImagePage(
    imageUrl: String,
    onTap: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val scale = remember { Animatable(1f) }
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    val dragY = remember { Animatable(0f) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    SubcomposeAsyncImage(
        model = imageUrl,
        contentDescription = "图片预览",
        contentScale = ContentScale.Fit,
        loading = {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.32f)) {
                    Box(modifier = Modifier.padding(14.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 2.5.dp,
                            color = Color.White,
                        )
                    }
                }
            }
        },
        error = {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Default.BrokenImage, contentDescription = null, tint = Color.White)
                    Text(text = "图片加载失败", color = Color.White)
                }
            }
        },
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { canvasSize = it }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { tapPos ->
                        scope.launch {
                            if (scale.value > 1.05f) {
                                animateReset(scale, offsetX, offsetY)
                            } else {
                                val target = 2.5f
                                val factor = target / scale.value
                                val curOffset = Offset(offsetX.value, offsetY.value)
                                val next = tapPos - (tapPos - curOffset) * factor
                                kotlinx.coroutines.coroutineScope {
                                    launch { scale.animateTo(target, tween(280)) }
                                    launch { offsetX.animateTo(next.x, tween(280)) }
                                    launch { offsetY.animateTo(next.y, tween(280)) }
                                }
                            }
                        }
                    },
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    scope.launch {
                        scale.stop()
                        offsetX.stop()
                        offsetY.stop()
                    }

                    var usedMultiTouch = false
                    do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        when {
                            pressed.size >= 2 -> {
                                usedMultiTouch = true
                                val zoom = event.calculateZoom()
                                val pan = event.calculatePan()
                                val centroid = event.calculateCentroid(useCurrent = true)

                                val curScale = scale.value
                                val nextScale = (curScale * zoom).coerceIn(0.5f, 5f)
                                val factor = if (curScale > 0f) nextScale / curScale else 1f
                                val curOffset = Offset(offsetX.value, offsetY.value)
                                val nextOffset = centroid - (centroid - curOffset) * factor + pan

                                scope.launch {
                                    scale.snapTo(nextScale)
                                    offsetX.snapTo(nextOffset.x)
                                    offsetY.snapTo(nextOffset.y)
                                }
                                pressed.forEach { it.consume() }
                            }

                            pressed.size == 1 && scale.value > 1.05f -> {
                                val change = pressed[0]
                                val delta = change.positionChange()
                                if (delta != Offset.Zero) {
                                    scope.launch {
                                        offsetX.snapTo(offsetX.value + delta.x)
                                        offsetY.snapTo(offsetY.value + delta.y)
                                    }
                                    change.consume()
                                }
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    if (usedMultiTouch || scale.value > 1.01f) {
                        settleAfterRelease(scope, scale, offsetX, offsetY, canvasSize)
                    }
                }
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onVerticalDrag = { change, delta ->
                        if (scale.value > 1.05f) return@detectVerticalDragGestures
                        if (delta > 0f || dragY.value > 0f) {
                            scope.launch {
                                dragY.stop()
                                val next = (dragY.value + delta * 0.72f).coerceAtLeast(0f)
                                dragY.snapTo(next)
                            }
                            change.consume()
                        }
                    },
                    onDragEnd = {
                        val dismissThreshold = (canvasSize.height * 0.18f).coerceAtLeast(140f)
                        if (dragY.value > dismissThreshold) {
                            onDismiss()
                        } else {
                            scope.launch {
                                dragY.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(
                                        dampingRatio = 0.82f,
                                        stiffness = Spring.StiffnessMediumLow,
                                    ),
                                )
                            }
                        }
                    },
                    onDragCancel = {
                        scope.launch {
                            dragY.animateTo(
                                targetValue = 0f,
                                animationSpec = spring(
                                    dampingRatio = 0.82f,
                                    stiffness = Spring.StiffnessMediumLow,
                                ),
                            )
                        }
                    },
                )
            }
            .graphicsLayer {
                val dismissScale =
                    (1f - (dragY.value / (canvasSize.height.coerceAtLeast(1) * 3f))).coerceIn(0.92f, 1f)
                transformOrigin = TransformOrigin(0f, 0f)
                scaleX = scale.value * dismissScale
                scaleY = scale.value * dismissScale
                translationX = offsetX.value
                translationY = offsetY.value + dragY.value
            },
    )
}

private fun settleAfterRelease(
    scope: kotlinx.coroutines.CoroutineScope,
    scale: Animatable<Float, *>,
    offsetX: Animatable<Float, *>,
    offsetY: Animatable<Float, *>,
    canvasSize: IntSize,
) {
    val s = scale.value
    val ox = offsetX.value
    val oy = offsetY.value

    if (s <= 1f) {
        scope.launch { scale.animateTo(1f, spring(stiffness = Spring.StiffnessMediumLow)) }
        scope.launch { offsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
        scope.launch { offsetY.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
        return
    }

    val maxX = ((s - 1f) * canvasSize.width / 2f).coerceAtLeast(0f)
    val maxY = ((s - 1f) * canvasSize.height / 2f).coerceAtLeast(0f)
    val tx = ox.coerceIn(-maxX, maxX)
    val ty = oy.coerceIn(-maxY, maxY)
    if (tx != ox) scope.launch { offsetX.animateTo(tx, spring()) }
    if (ty != oy) scope.launch { offsetY.animateTo(ty, spring()) }
}

private suspend fun animateReset(
    scale: Animatable<Float, *>,
    offsetX: Animatable<Float, *>,
    offsetY: Animatable<Float, *>,
) {
    kotlinx.coroutines.coroutineScope {
        launch { scale.animateTo(1f, tween(260)) }
        launch { offsetX.animateTo(0f, tween(260)) }
        launch { offsetY.animateTo(0f, tween(260)) }
    }
}

@Composable
private fun SavePillButton(
    saved: Boolean,
    loading: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.clip(RoundedCornerShape(50)),
        color = Color.Black.copy(alpha = 0.55f),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (saved) Icons.Default.Check else Icons.Default.Download,
                contentDescription = null,
                tint = Color.White,
            )
            Spacer(modifier = Modifier.padding(end = 6.dp))
            Text(
                text = when {
                    loading -> "保存中…"
                    saved -> "已保存"
                    else -> "保存到相册"
                },
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

private suspend fun saveImageToGallery(context: Context, url: String): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        val bytes = downloadBytes(url) ?: return@withContext false
        val displayName = "memory_${System.currentTimeMillis()}.jpg"
        val mime = "image/jpeg"
        val resolver = context.contentResolver

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, mime)
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Memory")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return@withContext false
        resolver.openOutputStream(uri)?.use { os -> os.write(bytes) }
            ?: return@withContext false
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        true
    }.getOrElse {
        android.util.Log.e("Memory/ImagePreview", "saveImageToGallery failed", it)
        false
    }
}

private fun downloadBytes(url: String): ByteArray? {
    return runCatching {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "Memory/Android")
        }
        conn.connect()
        try {
            if (conn.responseCode !in 200..299) return null
            conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }.getOrElse {
        android.util.Log.e("Memory/ImagePreview", "downloadBytes failed", it)
        null
    }
}
