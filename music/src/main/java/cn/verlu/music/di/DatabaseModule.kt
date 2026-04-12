package cn.verlu.music.di

import android.app.Application
import androidx.room.Room
import cn.verlu.music.data.local.MusicDatabase
import cn.verlu.music.data.local.dao.TrackDao
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
            "music.db"
        ).build()
    }

    @Provides
    @Singleton
    fun provideTrackDao(db: MusicDatabase): TrackDao {
        return db.trackDao
    }
}
