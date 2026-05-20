package cn.verlu.lulu.feature.sync.di

import android.content.Context
import androidx.room.Room
import cn.verlu.lulu.core.feature.LuluDatabaseNames
import cn.verlu.lulu.feature.sync.data.local.AppDatabase
import cn.verlu.lulu.feature.sync.data.local.BatteryLevelDao
import cn.verlu.lulu.feature.sync.data.local.ScreenTimeReportDao
import cn.verlu.lulu.feature.sync.data.local.TemperatureLevelDao
import cn.verlu.lulu.feature.sync.data.local.WeatherSnapshotDao
import cn.verlu.lulu.feature.sync.data.repository.BatteryRepositoryImpl
import cn.verlu.lulu.feature.sync.data.repository.ScreenTimeRemoteRepositoryImpl
import cn.verlu.lulu.feature.sync.data.repository.SyncedScreenTimeReportsRepositoryImpl
import cn.verlu.lulu.feature.sync.data.repository.WeatherRepositoryImpl
import cn.verlu.lulu.feature.sync.data.stats.ScreenTimeRepositoryImpl
import cn.verlu.lulu.feature.sync.domain.repository.BatteryRepository
import cn.verlu.lulu.feature.sync.domain.repository.ScreenTimeRemoteRepository
import cn.verlu.lulu.feature.sync.domain.repository.ScreenTimeRepository
import cn.verlu.lulu.feature.sync.domain.repository.SyncedScreenTimeReportsRepository
import cn.verlu.lulu.feature.sync.domain.repository.WeatherRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, LuluDatabaseNames.SYNC)
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
    fun provideTemperatureRepository(impl: cn.verlu.lulu.feature.sync.data.repository.TemperatureRepositoryImpl): cn.verlu.lulu.feature.sync.domain.repository.TemperatureRepository = impl

    @Provides
    @Singleton
    fun provideWeatherRepository(impl: WeatherRepositoryImpl): WeatherRepository = impl

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
}
