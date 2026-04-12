package cn.verlu.sync.di

import android.content.Context
import androidx.room.Room
import cn.verlu.sync.data.local.AppDatabase
import cn.verlu.sync.data.local.BatteryLevelDao
import cn.verlu.sync.data.local.ScreenTimeReportDao
import cn.verlu.sync.data.local.TemperatureLevelDao
import cn.verlu.sync.data.local.WeatherSnapshotDao
import cn.verlu.sync.data.remote.SupabaseConfig
import cn.verlu.sync.data.repository.BatteryRepositoryImpl
import cn.verlu.sync.data.repository.ScreenTimeRemoteRepositoryImpl
import cn.verlu.sync.data.repository.SyncedScreenTimeReportsRepositoryImpl
import cn.verlu.sync.data.repository.WeatherRepositoryImpl
import cn.verlu.sync.data.stats.ScreenTimeRepositoryImpl
import cn.verlu.sync.domain.repository.BatteryRepository
import cn.verlu.sync.domain.repository.ScreenTimeRemoteRepository
import cn.verlu.sync.domain.repository.ScreenTimeRepository
import cn.verlu.sync.domain.repository.SyncedScreenTimeReportsRepository
import cn.verlu.sync.domain.repository.WeatherRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.DEFAULT

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "sync.db")
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
                AppDatabase.MIGRATION_5_6,
                AppDatabase.MIGRATION_6_7
            )
            .build()

    @Provides
    @Singleton
    fun provideBatteryDao(db: AppDatabase): BatteryLevelDao = db.batteryLevelDao()

    @Provides
    @Singleton
    fun provideScreenTimeReportDao(db: AppDatabase): ScreenTimeReportDao =
        db.screenTimeReportDao()

    @Provides
    @Singleton
    fun provideTemperatureDao(db: AppDatabase): TemperatureLevelDao = db.temperatureLevelDao()

    @Provides
    @Singleton
    fun provideWeatherSnapshotDao(db: AppDatabase): WeatherSnapshotDao = db.weatherSnapshotDao()

    @Provides
    @Singleton
    fun provideFusedLocationClient(
        @ApplicationContext context: Context
    ): FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    @OptIn(SupabaseInternal::class)
    @Provides
    @Singleton
    fun provideSupabaseClient(): SupabaseClient =
        createSupabaseClient(
            supabaseUrl = SupabaseConfig.URL,
            supabaseKey = SupabaseConfig.ANON_KEY
        ) {
            requestTimeout = 60.seconds
            install(Auth) {
                // 处理 OAuth / implicit / PKCE 回跳的 deeplink：
                // AndroidManifest 里要保持 scheme/host 一致。
                scheme = "verlusync"
                host = "login"
                // 走 PKCE：兼容主流 OAuth provider 的安全推荐配置。
                flowType = FlowType.PKCE
            }
            install(Postgrest)
            install(Realtime)
            install(io.github.jan.supabase.functions.Functions)
            httpConfig {
                install(Logging) {
                    level = LogLevel.BODY
                    logger = Logger.DEFAULT
                }
            }
        }

    @Provides
    @Singleton
    fun provideBatteryRepository(impl: BatteryRepositoryImpl): BatteryRepository = impl

    @Provides
    @Singleton
    fun provideScreenTimeRepository(impl: ScreenTimeRepositoryImpl): ScreenTimeRepository = impl

    @Provides
    @Singleton
    fun provideScreenTimeRemoteRepository(
        impl: ScreenTimeRemoteRepositoryImpl
    ): ScreenTimeRemoteRepository = impl

    @Provides
    @Singleton
    fun provideSyncedScreenTimeReportsRepository(
        impl: SyncedScreenTimeReportsRepositoryImpl
    ): SyncedScreenTimeReportsRepository = impl

    @Provides
    @Singleton
    fun provideTemperatureRepository(impl: cn.verlu.sync.data.repository.TemperatureRepositoryImpl): cn.verlu.sync.domain.repository.TemperatureRepository = impl

    @Provides
    @Singleton
    fun provideWeatherRepository(impl: WeatherRepositoryImpl): WeatherRepository = impl

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
}
