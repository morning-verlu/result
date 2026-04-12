package cn.verlu.music.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "hidden_tracks")
data class HiddenTrackEntity(
    @PrimaryKey val mediaId: Long,
    val hiddenAt: Long = System.currentTimeMillis()
)
