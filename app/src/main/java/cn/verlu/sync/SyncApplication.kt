package cn.verlu.sync

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SyncApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // coil-svg 等解码器通过 ServiceLoader 自动注册；网络拉取需 coil-network-okhttp 依赖。
        SingletonImageLoader.setSafe { context ->
            ImageLoader.Builder(context).build()
        }
    }
}
