package cn.verlu.lulu.feature.talk.presentation.chat.stickers

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition

private const val ASSET_PREFIX = "asset:///"

/**
 * 渲染一张 Lottie 贴纸。`url` 支持：
 *  - `asset:///stickers/lottie/xxx.json` （应用内置）
 *  - `https://...json` （远程 CDN）
 */
@Composable
fun LottieStickerView(
    url: String,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    iterations: Int = LottieConstants.IterateForever,
    autoPlay: Boolean = true,
) {
    val spec = remember(url) {
        when {
            url.startsWith(ASSET_PREFIX) -> LottieCompositionSpec.Asset(url.removePrefix(ASSET_PREFIX))
            url.startsWith("http://") || url.startsWith("https://") -> LottieCompositionSpec.Url(url)
            else -> LottieCompositionSpec.Asset(url)
        }
    }
    val compositionResult = rememberLottieComposition(spec)
    val composition = compositionResult.value
    val progressState = animateLottieCompositionAsState(
        composition = composition,
        iterations = iterations,
        isPlaying = autoPlay,
    )
    LottieAnimation(
        composition = composition,
        progress = { progressState.value },
        modifier = modifier.size(size),
    )
}
