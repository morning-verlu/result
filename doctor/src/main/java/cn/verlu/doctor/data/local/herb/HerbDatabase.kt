package cn.verlu.doctor.data.local.herb

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        HerbGlobalCacheEntity::class,
        HerbHomePreviewsEntity::class,
        HerbBrowseCacheEntity::class,
        HerbArticleCacheEntity::class,
        HerbSearchCacheEntity::class,
        HerbSpotlightEntity::class,
        HerbFavoriteEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class HerbDatabase : RoomDatabase() {
    abstract fun herbDao(): HerbDao
}
