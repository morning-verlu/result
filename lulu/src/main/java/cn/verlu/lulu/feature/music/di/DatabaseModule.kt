package cn.verlu.lulu.feature.music.di

import android.app.Application
import androidx.room.Room
import cn.verlu.lulu.core.feature.LuluDatabaseNames
import cn.verlu.lulu.feature.music.data.local.MusicDatabase
import cn.verlu.lulu.feature.music.data.local.dao.TrackDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideMusicDatabase(app: Application): MusicDatabase {
        return Room.databaseBuilder(
            app,
            MusicDatabase::class.java,
            LuluDatabaseNames.MUSIC
        ).build()
    }

    @Provides
    @Singleton
    fun provideTrackDao(db: MusicDatabase): TrackDao {
        return db.trackDao
    }
}
