package cn.verlu.doctor.presentation.herb.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.coil3.Coil3ImageTransformerImpl
import com.mikepenz.markdown.model.ImageData
import com.mikepenz.markdown.model.ImageTransformer

/**
 * 在 Coil3 渲染基础上：正文内图片完整可见（Fit + 宽度撑满）、居中，并支持点击进全屏图库。
 *
 * 库默认常见问题是 [ContentScale] 或高度约束导致裁切、甚至出现极扁的「一条线」预览。
 */
class HerbClickableCoil3ImageTransformer(
    private val onImageClick: (imageUrl: String) -> Unit,
) : ImageTransformer {
    @Composable
    override fun transform(link: String): ImageData {
        val base = Coil3ImageTransformerImpl.transform(link)
        return base.copy(
            modifier = base.modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .wrapContentHeight(align = Alignment.Top, unbounded = true)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) {
                    onImageClick(link)
                },
            contentScale = ContentScale.Fit,
            alignment = Alignment.Center,
        )
    }
}
