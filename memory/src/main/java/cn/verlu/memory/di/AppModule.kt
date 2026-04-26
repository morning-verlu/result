package cn.verlu.memory.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import cn.verlu.memory.data.local.MemoryDatabase
import cn.verlu.memory.data.local.MemorySettingsStore
import cn.verlu.memory.data.local.dao.MemoryEntryDao
import cn.verlu.memory.data.local.dao.TombstoneDao
import cn.verlu.memory.data.repository.FileLifeStreamRepository
import cn.verlu.memory.data.remote.SupabaseConfig
import cn.verlu.memory.data.repository.SupabaseAuthRepository
import cn.verlu.memory.domain.repository.AuthRepository
import cn.verlu.memory.domain.repository.LifeStreamRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
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
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideMemoryDatabase(
        @ApplicationContext context: Context,
    ): MemoryDatabase = Room.databaseBuilder(
        context,
        MemoryDatabase::class.java,
        "memory.db",
    ).addMigrations(MIGRATION_3_4).build()

    @Provides
    @Singleton
    fun provideMemoryEntryDao(db: MemoryDatabase): MemoryEntryDao = db.memoryEntryDao()

    @Provides
    @Singleton
    fun provideTombstoneDao(db: MemoryDatabase): TombstoneDao = db.tombstoneDao()

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
                scheme = "memoryapp"
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
    @Singleton
    fun provideAuthRepository(
        supabase: SupabaseClient,
    ): AuthRepository = SupabaseAuthRepository(supabase)

    @Provides
    @Singleton
    fun provideLifeStreamRepository(
        @ApplicationContext context: Context,
        supabase: SupabaseClient,
        memoryEntryDao: MemoryEntryDao,
        tombstoneDao: TombstoneDao,
        settingsStore: MemorySettingsStore,
    ): LifeStreamRepository = FileLifeStreamRepository(
        context = context,
        supabase = supabase,
        memoryEntryDao = memoryEntryDao,
        tombstoneDao = tombstoneDao,
        settingsStore = settingsStore,
    )
}

/**
 * v3 → v4：schema 不变，只是建立显式迁移机制以保护现有数据。
 * 未来修改 schema 时，在此文件添加新的 Migration 并在 addMigrations() 里注册。
 */
private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // schema 不变，无需操作
    }
}
