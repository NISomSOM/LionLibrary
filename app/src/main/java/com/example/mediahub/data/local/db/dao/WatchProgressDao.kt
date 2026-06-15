package com.example.mediahub.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.mediahub.data.local.db.entity.JumpBackInEntity
import com.example.mediahub.data.local.db.entity.WatchProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchProgressDao {

    @Query(
        """
        SELECT 
            m.id AS mediaId, 
            m.title AS mediaTitle, 
            m.posterPath, 
            m.mediaType,
            e.id AS episodeId, 
            e.title AS episodeTitle, 
            e.seasonNumber, 
            e.episodeNumber, 
            COALESCE(e.filePath, m.filePath) AS filePath,
            MAX(w.lastWatched) AS lastWatched
        FROM watch_progress w
        INNER JOIN media m ON m.id = w.mediaId
        LEFT JOIN episodes e ON e.id = w.episodeId
        WHERE w.completed = 0
        GROUP BY m.id
        ORDER BY lastWatched DESC
        LIMIT 20
        """
    )
    fun getJumpBackInItems(): Flow<List<JumpBackInEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: WatchProgressEntity)

    @Query("SELECT * FROM watch_progress WHERE mediaId = :mediaId AND episodeId = :episodeId")
    suspend fun getProgress(mediaId: Long, episodeId: Long): WatchProgressEntity?

    @Query("SELECT * FROM watch_progress WHERE mediaId = :mediaId")
    fun getProgressForMedia(mediaId: Long): Flow<List<WatchProgressEntity>>

    @Query("DELETE FROM watch_progress")
    suspend fun clearAll()
}
