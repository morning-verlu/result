package cn.verlu.doctor.di

import android.content.Context
import androidx.room.Room
import cn.verlu.doctor.data.local.herb.HerbDao
import cn.verlu.doctor.data.local.herb.HerbDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object HerbDatabaseModule {

    @Provides
    @Singleton
    fun provideHerbDatabase(@ApplicationContext context: Context): HerbDatabase =
        Room.databaseBuilder(
            context,
            HerbDatabase::class.java,
            "herb_cache.db",
        ).fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    @Singleton
    fun provideHerbDao(db: HerbDatabase): HerbDao = db.herbDao()
}
