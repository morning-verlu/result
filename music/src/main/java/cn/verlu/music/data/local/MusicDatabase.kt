package cn.verlu.music.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import cn.verlu.music.data.local.dao.TrackDao
import cn.verlu.music.data.local.entity.FavoriteTrackEntity
import cn.verlu.music.data.local.entity.HiddenTrackEntity

@Database(
    entities = [
        HiddenTrackEntity::class,
        FavoriteTrackEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class MusicDatabase : RoomDatabase() {
    abstract val trackDao: TrackDao
}
