package com.singam.lionlibrary.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.singam.lionlibrary.data.local.db.entity.MediaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {

    @Query("SELECT * FROM media WHERE mediaType = :type AND isUnidentified = 0 ORDER BY title ASC")
    fun getByType(type: String): Flow<List<MediaEntity>>

    @Query(
        """
        SELECT * FROM media 
        WHERE (title LIKE '%' || :query || '%' 
        OR overview LIKE '%' || :query || '%' 
        OR genres LIKE '%' || :query || '%')
        AND isUnidentified = 0
        ORDER BY title ASC
        LIMIT 100
        """
    )
    fun search(query: String): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media WHERE id = :id")
    suspend fun getById(id: Long): MediaEntity?

    @Query("SELECT * FROM media WHERE tmdbId = :tmdbId LIMIT 1")
    suspend fun getByTmdbId(tmdbId: Int): MediaEntity?


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(media: MediaEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<MediaEntity>)

    @Query("SELECT * FROM media WHERE isUnidentified = 0 ORDER BY lastUpdated DESC LIMIT 20")
    fun getRecentlyAdded(): Flow<List<MediaEntity>>


    @Query("SELECT * FROM media WHERE filePath = :filePath LIMIT 1")
    suspend fun getByFilePath(filePath: String): MediaEntity?


}

