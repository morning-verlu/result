package cn.verlu.music.data.repository

import androidx.media3.datasource.cache.SimpleCache
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayerCacheRepository @Inject constructor(
    private val cache: SimpleCache
) {
    companion object {
        /** 与 [cn.verlu.music.di.MediaModule] 中 LeastRecentlyUsedCacheEvictor 上限一致 */
        const val MAX_CACHE_BYTES = 512L * 1024L * 1024L
    }

    /** ExoPlayer 流缓存当前占用（字节）。 */
    fun usedBytes(): Long = cache.cacheSpace

    /** 清空在线播放缓存条目（不卸载 SimpleCache 实例）。 */
    fun clearAll() {
        synchronized(cache) {
            val keys = cache.keys.toList()
            for (key in keys) {
                cache.removeResource(key)
            }
        }
    }
}
