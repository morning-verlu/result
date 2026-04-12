package cn.verlu.doctor.data.herb

import cn.verlu.doctor.data.herb.dto.ArticleMeta
import cn.verlu.doctor.data.herb.dto.ArticlePreview
import kotlinx.serialization.json.JsonObject

data class HerbHomeCached(
    val stats: JsonObject?,
    val healthIndexed: Int?,
    val previews: List<ArticlePreview>,
)

data class HerbBrowseCached(
    val items: List<ArticleMeta>,
    val total: Int?,
    val nextOffset: Int,
    val hasMore: Boolean,
)
