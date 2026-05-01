package cn.verlu.lulu.feature.talk.presentation.chat.stickers

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 贴纸消息内容采用统一约定：`sticker://<packId>/<stickerId>`，
 * 由 [StickerRegistry] 解析为对应的 Lottie 资源 URI（assets:/// 或 https://）。
 *
 * 这样：
 *  - 消息体短小、跨端可解析
 *  - 资源路径可以集中替换/升级，未来可改为远程 CDN 而消息内容不变
 *  - 旧版客户端如果不识别 sticker 类型，依然能看到一个 `[表情]` 占位
 */
const val STICKER_SCHEME = "sticker://"

@Serializable
data class StickerPackManifest(
    val packs: List<StickerPack> = emptyList(),
)

@Serializable
data class StickerPack(
    val id: String,
    val name: String,
    val cover: String? = null,
    val stickers: List<StickerItem> = emptyList(),
)

@Serializable
data class StickerItem(
    val id: String,
    val name: String? = null,
    /** Lottie / 图片资源 URI；以 `assets:///` 开头表示本地 assets，或 `https://` 远程 URL。 */
    val url: String,
    /** 是否为 Lottie 动画 (json)。否则按静态图片渲染。 */
    val lottie: Boolean = true,
    /** 静态预览图，挑选器列表展示用，缺省则直接渲染 Lottie。 */
    val preview: String? = null,
)

object StickerRegistry {
    private const val MANIFEST_PATH = "stickers/manifest.json"

    @Volatile
    private var cached: StickerPackManifest? = null

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun encodeStickerContent(packId: String, stickerId: String): String =
        "$STICKER_SCHEME$packId/$stickerId"

    fun parseStickerContent(content: String): Pair<String, String>? {
        if (!content.startsWith(STICKER_SCHEME)) return null
        val tail = content.removePrefix(STICKER_SCHEME)
        val parts = tail.split('/', limit = 2)
        if (parts.size != 2 || parts.any { it.isBlank() }) return null
        return parts[0] to parts[1]
    }

    suspend fun load(context: Context): StickerPackManifest {
        cached?.let { return it }
        return withContext(Dispatchers.IO) {
            val parsed = runCatching {
                val text = context.assets.open(MANIFEST_PATH).bufferedReader().use { it.readText() }
                json.decodeFromString<StickerPackManifest>(text)
            }.getOrElse { StickerPackManifest() }
            cached = parsed
            parsed
        }
    }

    /**
     * 根据消息 content（`sticker://pack/id`）查找对应的 Lottie 资源 URL。
     * 找不到时返回 null。
     */
    fun resolve(manifest: StickerPackManifest, content: String): StickerItem? {
        val (packId, stickerId) = parseStickerContent(content) ?: return null
        val pack = manifest.packs.firstOrNull { it.id == packId } ?: return null
        return pack.stickers.firstOrNull { it.id == stickerId }
    }
}

@Composable
fun rememberStickerManifest(): MutableState<StickerPackManifest?> {
    val context = LocalContext.current
    val state = remember { mutableStateOf<StickerPackManifest?>(null) }
    LaunchedEffect(Unit) {
        if (state.value == null) {
            state.value = StickerRegistry.load(context.applicationContext)
        }
    }
    return state
}
