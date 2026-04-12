package cn.verlu.music.di

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import cn.verlu.music.data.repository.PlayerCacheRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MediaModule {

    private const val CACHE_DIR = "exoplayer_stream_cache"

    @Provides
    @Singleton
    fun provideSimpleCache(@ApplicationContext context: Context): SimpleCache {
        val dir = File(context.cacheDir, CACHE_DIR)
        return SimpleCache(
            dir,
            LeastRecentlyUsedCacheEvictor(PlayerCacheRepository.MAX_CACHE_BYTES),
            StandaloneDatabaseProvider(context)
        )
    }

    @Provides
    @Singleton
    fun provideCacheDataSourceFactory(
        @ApplicationContext context: Context,
        cache: SimpleCache
    ): CacheDataSource.Factory {
        val httpFactory = DefaultHttpDataSource.Factory()
        val upstream = DefaultDataSource.Factory(context, httpFactory)
        return CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstream)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }
}
