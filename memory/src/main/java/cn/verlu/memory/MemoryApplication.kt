package cn.verlu.memory

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.CachePolicy
import dagger.hilt.android.HiltAndroidApp
import okio.Path.Companion.toOkioPath

/**
 * 自定义 Coil 3 ImageLoader：
 * - 内存缓存：64MB
 * - Disk 缓存：256MB，key = 请求 URL，与 HTTP Cache-Control 无关。
 *   绕过 Supabase signed URL 可能携带的 Cache-Control: no-store，
 *   确保图片被持久缓存——导航回来直接命中内存/磁盘缓存，无闪烁、无网络请求。
 */
@HiltAndroidApp
class MemoryApplication : Application(), SingletonImageLoader.Factory {

    override fun newImageLoader(context: Context): ImageLoader =
        ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizeBytes(64 * 1024 * 1024) // 64MB
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("coil-image-cache").toOkioPath())
                    .maxSizeBytes(256L * 1024 * 1024) // 256MB
                    .build()
            }
            .networkCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .build()
}
