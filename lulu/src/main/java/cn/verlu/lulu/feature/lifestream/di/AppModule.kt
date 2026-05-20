package cn.verlu.lulu.feature.lifestream.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import cn.verlu.lulu.core.feature.LuluDatabaseNames
import cn.verlu.lulu.feature.lifestream.data.local.MemoryDatabase
import cn.verlu.lulu.feature.lifestream.data.local.MemorySettingsStore
import cn.verlu.lulu.feature.lifestream.data.local.dao.MemoryEntryDao
import cn.verlu.lulu.feature.lifestream.data.local.dao.TombstoneDao
import cn.verlu.lulu.feature.lifestream.data.repository.FileLifeStreamRepository
import cn.verlu.lulu.feature.lifestream.data.repository.SupabaseAuthRepository
import cn.verlu.lulu.feature.lifestream.domain.repository.AuthRepository
import cn.verlu.lulu.feature.lifestream.domain.repository.LifeStreamRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Singleton

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
        LuluDatabaseNames.LIFE_STREAM,
    ).addMigrations(MIGRATION_3_4, MIGRATION_4_5).build()

    @Provides
    @Singleton
    fun provideMemoryEntryDao(db: MemoryDatabase): MemoryEntryDao = db.memoryEntryDao()

    @Provides
    @Singleton
    fun provideTombstoneDao(db: MemoryDatabase): TombstoneDao = db.tombstoneDao()

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
        luluMemoryEntryDao: cn.verlu.lulu.data.local.dao.MemoryEntryDao,
        tombstoneDao: TombstoneDao,
        settingsStore: MemorySettingsStore,
    ): LifeStreamRepository = FileLifeStreamRepository(
        context = context,
        supabase = supabase,
        memoryEntryDao = memoryEntryDao,
        luluMemoryEntryDao = luluMemoryEntryDao,
        tombstoneDao = tombstoneDao,
        settingsStore = settingsStore,
    )
}

/**
 * v3 → v4：schema 不变，只是建立显式迁移机制以保护现有数据。
 * v4 → v5：schema 不变，保持数据库版本单调递增，避免已装过 v5 的包发生降级崩溃。
 * 未来修改 schema 时，在此文件添加新的 Migration 并在 addMigrations() 里注册。
 */
private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // schema 不变，无需操作
    }
}

private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // schema 不变，无需操作
    }
}
