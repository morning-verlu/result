package cn.verlu.lulu.feature.doctor.di

import android.content.Context
import androidx.room.Room
import cn.verlu.lulu.core.feature.LuluDatabaseNames
import cn.verlu.lulu.feature.doctor.data.local.herb.HerbDao
import cn.verlu.lulu.feature.doctor.data.local.herb.HerbDatabase
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
            LuluDatabaseNames.DOCTOR,
        ).fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    @Singleton
    fun provideHerbDao(db: HerbDatabase): HerbDao = db.herbDao()
}
