package cn.verlu.music.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorite_tracks")
data class FavoriteTrackEntity(
    @PrimaryKey val mediaId: Long,
    val addedAt: Long = System.currentTimeMillis()
)
