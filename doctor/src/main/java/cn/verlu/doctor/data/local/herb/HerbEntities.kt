package cn.verlu.doctor.data.local.herb

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "herb_global_cache")
data class HerbGlobalCacheEntity(
    @PrimaryKey val id: Int = 1,
    val statsJson: String?,
    val healthIndexed: Int?,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "herb_home_previews",
    primaryKeys = ["collectionParam"],
)
data class HerbHomePreviewsEntity(
    val collectionParam: String,
    val previewsJson: String,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "herb_browse_cache",
    primaryKeys = ["collectionParam"],
)
data class HerbBrowseCacheEntity(
    val collectionParam: String,
    val total: Int?,
    val itemsJson: String,
    val nextOffset: Int,
    val hasMore: Boolean,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "herb_article_cache")
data class HerbArticleCacheEntity(
    @PrimaryKey val path: String,
    val detailJson: String,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "herb_search_cache")
data class HerbSearchCacheEntity(
    @PrimaryKey val queryNorm: String,
    val resultsJson: String,
    val updatedAt: Long = System.currentTimeMillis(),
)

/** 首页「随机一篇」缓存（全部卷，单条预览） */
@Entity(tableName = "herb_spotlight")
data class HerbSpotlightEntity(
    @PrimaryKey val id: Int = 1,
    val previewJson: String,
    val updatedAt: Long = System.currentTimeMillis(),
)

/** 本地收藏（仅 Room，不同步服务端） */
@Entity(tableName = "herb_favorites")
data class HerbFavoriteEntity(
    @PrimaryKey val path: String,
    val title: String,
    val collection: String,
    val serial: Int?,
    val savedAt: Long = System.currentTimeMillis(),
)
