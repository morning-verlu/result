package cn.verlu.doctor.presentation.herb.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage

/** 双击放大使用的固定倍数（相对 1:1）；再次双击恢复 1:1。 */
private const val FIXED_ZOOM_SCALE = 2.5f

/**
 * 全屏看图：左右滑动切换；双击在固定倍数与 1:1 间切换（不抢水平滑动）。
 */
@Composable
fun HerbArticleImageGallery(
    urls: List<String>,
    initialIndex: Int,
    onDismiss: () -> Unit,
) {
    if (urls.isEmpty()) return
    val last = (urls.size - 1).coerceAtLeast(0)
    val start = initialIndex.coerceIn(0, last)
    val pagerState = rememberPagerState(pageCount = { urls.size }, initialPage = start)

    LaunchedEffect(urls, initialIndex) {
        val p = initialIndex.coerceIn(0, last)
        if (pagerState.currentPage != p) pagerState.scrollToPage(p)
    }

    BackHandler(onBack = onDismiss)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1,
            ) { page ->
                ZoomableFullscreenImage(url = urls[page], pageKey = page)
            }
            Text(
                text = "${pagerState.currentPage + 1} / ${urls.size}",
                color = Color.White.copy(alpha = 0.9f),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 8.dp),
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(4.dp),
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "关闭",
                    tint = Color.White,
                )
            }
        }
    }
}

@Composable
private fun ZoomableFullscreenImage(
    url: String,
    pageKey: Int,
) {
    val scale = remember(pageKey) { mutableFloatStateOf(1f) }

    LaunchedEffect(pageKey) {
        scale.floatValue = 1f
    }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(pageKey) {
                detectTapGestures(
                    onDoubleTap = {
                        scale.floatValue = if (scale.floatValue <= 1.01f) {
                            FIXED_ZOOM_SCALE
                        } else {
                            1f
                        }
                    },
                )
            }
            .graphicsLayer {
                scaleX = scale.floatValue
                scaleY = scale.floatValue
                transformOrigin = TransformOrigin(0.5f, 0.5f)
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = url,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )
    }
}
