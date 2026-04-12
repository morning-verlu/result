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
import com.mikepenz.markdown.coil2.Coil2ImageTransformerImpl
import com.mikepenz.markdown.model.ImageData
import com.mikepenz.markdown.model.ImageTransformer

/**
 * 在 Coil2 渲染基础上：正文内图片完整可见（Fit + 宽度撑满）、居中，并支持点击进全屏图库。
 *
 * 库默认常见问题是 [ContentScale] 或高度约束导致裁切、甚至出现极扁的「一条线」预览。
 */
class HerbClickableCoil2ImageTransformer(
    private val onImageClick: (imageUrl: String) -> Unit,
) : ImageTransformer {
    @Composable
    override fun transform(link: String): ImageData? {
        val base = Coil2ImageTransformerImpl.transform(link) ?: return null
        return base.copy(
            modifier = base.modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                // unbounded：避免部分父级给的高度上限过小导致被压成「一条线」
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
