package cn.verlu.lulu.di

import android.content.Context
import androidx.room.Room
import cn.verlu.lulu.data.chat.MemoryAwareChatRepository
import cn.verlu.lulu.data.cloud.EdgeFunctionCloudRepository
import cn.verlu.lulu.data.local.LuluDatabase
import cn.verlu.lulu.data.local.dao.ChatDao
import cn.verlu.lulu.data.local.dao.MemoryEntryDao
import cn.verlu.lulu.data.memory.RoomMemoryRepository
import cn.verlu.lulu.data.music.LocalMusicRepository
import cn.verlu.lulu.data.remote.SupabaseConfig
import cn.verlu.lulu.data.sync.AndroidBatteryStatusReader
import cn.verlu.lulu.data.sync.AndroidDeviceTemperatureStatusSource
import cn.verlu.lulu.data.sync.AndroidScreenTimeStatusSource
import cn.verlu.lulu.data.sync.AndroidWeatherStatusSource
import cn.verlu.lulu.data.sync.TodayStatusRepositoryImpl
import cn.verlu.lulu.domain.chat.ChatRepository
import cn.verlu.lulu.domain.cloud.CloudRepository
import cn.verlu.lulu.domain.memory.MemoryRepository
import cn.verlu.lulu.domain.music.MusicRepository
import cn.verlu.lulu.domain.sync.BatteryStatusReader
import cn.verlu.lulu.domain.sync.DeviceTemperatureStatusSource
import cn.verlu.lulu.domain.sync.ScreenTimeStatusSource
import cn.verlu.lulu.domain.sync.SyncStatusRepository
import cn.verlu.lulu.domain.sync.WeatherStatusSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @OptIn(SupabaseInternal::class)
    @Provides
    @Singleton
    fun provideSupabaseClient(): SupabaseClient =
        createSupabaseClient(
            supabaseUrl = SupabaseConfig.URL,
            supabaseKey = SupabaseConfig.ANON_KEY,
        ) {
            requestTimeout = 60.seconds
            install(Auth) {
                scheme = "luluapp"
                host = "login"
                flowType = FlowType.PKCE
            }
            install(Postgrest)
            install(Realtime)
            install(Functions)
            install(Storage)
            httpConfig {
                install(Logging) {
                    level = LogLevel.INFO
                }
            }
        }

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(
        @IoDispatcher dispatcher: CoroutineDispatcher,
    ): CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher)

    @Provides
    @Singleton
    fun provideLuluDatabase(
        @ApplicationContext context: Context,
    ): LuluDatabase =
        Room.databaseBuilder(context, LuluDatabase::class.java, "lulu.db")
            .addMigrations(*LuluDatabase.MIGRATIONS)
            .build()

    @Provides
    @Singleton
    fun provideMemoryEntryDao(db: LuluDatabase): MemoryEntryDao = db.memoryEntryDao()

    @Provides
    @Singleton
    fun provideChatDao(db: LuluDatabase): ChatDao = db.chatDao()

    @Provides
    @Singleton
    fun provideMemoryRepository(repository: RoomMemoryRepository): MemoryRepository = repository

    @Provides
    @Singleton
    fun provideCloudRepository(repository: EdgeFunctionCloudRepository): CloudRepository = repository

    @Provides
    @Singleton
    fun provideMusicRepository(repository: LocalMusicRepository): MusicRepository = repository

    @Provides
    @Singleton
    fun provideChatRepository(repository: MemoryAwareChatRepository): ChatRepository = repository

    @Provides
    @Singleton
    fun provideWeatherStatusSource(source: AndroidWeatherStatusSource): WeatherStatusSource = source

    @Provides
    @Singleton
    fun provideBatteryStatusReader(reader: AndroidBatteryStatusReader): BatteryStatusReader = reader

    @Provides
    @Singleton
    fun provideScreenTimeStatusSource(source: AndroidScreenTimeStatusSource): ScreenTimeStatusSource = source

    @Provides
    @Singleton
    fun provideDeviceTemperatureStatusSource(
        source: AndroidDeviceTemperatureStatusSource,
    ): DeviceTemperatureStatusSource = source

    @Provides
    @Singleton
    fun provideSyncStatusRepository(repository: TodayStatusRepositoryImpl): SyncStatusRepository = repository
}
