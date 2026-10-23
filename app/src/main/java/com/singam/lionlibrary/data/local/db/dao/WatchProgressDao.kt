package com.singam.lionlibrary.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.singam.lionlibrary.data.local.db.entity.JumpBackInEntity
import com.singam.lionlibrary.data.local.db.entity.WatchProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchProgressDao {

    @Query(
        """
        SELECT 
            m.id AS mediaId, 
            m.title AS mediaTitle, 
            m.posterPath, 
            m.backdropPath,
            m.mediaType,
            e.id AS episodeId, 
            e.title AS episodeTitle, 
            e.seasonNumber, 
            e.episodeNumber, 
            e.thumbnailPath,
            w.progress,
            w.completed,
            w.durationMs,
            w.lastPositionMs,
            COALESCE(e.filePath, m.filePath) AS filePath,
            w.lastWatched AS lastWatched
        FROM watch_progress w
        INNER JOIN media m ON m.id = w.mediaId
        LEFT JOIN episodes e ON e.id = w.episodeId
        WHERE w.isHidden = 0 AND w.episodeId = (
            SELECT latest.episodeId FROM watch_progress latest
            WHERE latest.mediaId=w.mediaId AND latest.isHidden=0
            ORDER BY latest.lastWatched DESC, latest.episodeId DESC LIMIT 1
        )
        ORDER BY lastWatched DESC, m.id ASC
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

    @Query("DELETE FROM watch_progress WHERE mediaId = :mediaId AND episodeId = :episodeId")
    suspend fun deleteProgress(mediaId: Long, episodeId: Long)

    @Query("DELETE FROM watch_progress WHERE mediaId = :mediaId")
    suspend fun deleteProgressForMedia(mediaId: Long)

    @Query("UPDATE watch_progress SET isHidden = 1 WHERE mediaId = :mediaId")
    suspend fun hideMediaFromJumpBackIn(mediaId: Long)

    @Query("DELETE FROM watch_progress") suspend fun clearAll()

    /** One transaction, one invalidation; timestamps still advance per episode as before. */
    @Transaction
    suspend fun markAllWatched(mediaId: Long, episodeIds: List<Long>) {
        for (episodeId in episodeIds) {
            val existing = getProgress(mediaId, episodeId)
            upsert(
                WatchProgressEntity(
                    mediaId = mediaId,
                    episodeId = episodeId,
                    progress = 1.0f,
                    lastPositionMs = existing?.lastPositionMs ?: 0L,
                    durationMs = existing?.durationMs ?: 0L,
                    lastWatched = System.currentTimeMillis(),
                    completed = true,
                    isHidden = false,
                )
            )
        }
    }
}
