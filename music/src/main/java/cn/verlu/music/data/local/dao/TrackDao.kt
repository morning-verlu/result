package cn.verlu.music.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import cn.verlu.music.data.local.entity.FavoriteTrackEntity
import cn.verlu.music.data.local.entity.HiddenTrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {

    @Query("SELECT mediaId FROM hidden_tracks")
    suspend fun getAllHiddenMediaIds(): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHiddenTrack(track: HiddenTrackEntity)

    @Query("DELETE FROM hidden_tracks WHERE mediaId IN (:ids)")
    suspend fun unhideTracks(ids: List<Long>)

    @Query("SELECT mediaId FROM favorite_tracks")
    fun observeFavoriteMediaIds(): Flow<List<Long>>
    
    @Query("SELECT mediaId FROM favorite_tracks")
    suspend fun getFavoriteMediaIds(): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavoriteTrack(track: FavoriteTrackEntity)

    @Query("DELETE FROM favorite_tracks WHERE mediaId = :mediaId")
    suspend fun deleteFavoriteTrack(mediaId: Long)
}
