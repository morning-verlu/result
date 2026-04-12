package cn.verlu.doctor.data.local.herb

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HerbDao {
    @Query("SELECT * FROM herb_global_cache WHERE id = 1 LIMIT 1")
    suspend fun getGlobal(): HerbGlobalCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGlobal(e: HerbGlobalCacheEntity)

    @Query("SELECT * FROM herb_home_previews WHERE collectionParam = :collection LIMIT 1")
    suspend fun getHomePreviews(collection: String): HerbHomePreviewsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHomePreviews(e: HerbHomePreviewsEntity)

    @Query("SELECT * FROM herb_browse_cache WHERE collectionParam = :collection LIMIT 1")
    suspend fun getBrowse(collection: String): HerbBrowseCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBrowse(e: HerbBrowseCacheEntity)

    @Query("SELECT * FROM herb_article_cache WHERE path = :path LIMIT 1")
    suspend fun getArticle(path: String): HerbArticleCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertArticle(e: HerbArticleCacheEntity)

    @Query("SELECT * FROM herb_search_cache WHERE queryNorm = :queryNorm LIMIT 1")
    suspend fun getSearch(queryNorm: String): HerbSearchCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSearch(e: HerbSearchCacheEntity)

    @Query("SELECT * FROM herb_spotlight WHERE id = 1 LIMIT 1")
    suspend fun getSpotlight(): HerbSpotlightEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSpotlight(e: HerbSpotlightEntity)

    @Query("SELECT * FROM herb_favorites ORDER BY savedAt DESC")
    fun observeFavorites(): Flow<List<HerbFavoriteEntity>>

    @Query("SELECT COUNT(*) FROM herb_favorites WHERE path = :path")
    fun observeFavoriteCount(path: String): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFavorite(e: HerbFavoriteEntity)

    @Query("DELETE FROM herb_favorites WHERE path = :path")
    suspend fun deleteFavorite(path: String)

    @Query("SELECT * FROM herb_favorites WHERE path = :path LIMIT 1")
    suspend fun getFavorite(path: String): HerbFavoriteEntity?
}
