package com.singam.lionlibrary.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.singam.lionlibrary.data.local.db.entity.EpisodeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EpisodeDao {

    @Query(
        """
        SELECT * FROM episodes 
        WHERE showId = :showId AND seasonNumber = :seasonNumber 
        ORDER BY episodeNumber ASC
        """
    )
    fun getEpisodesForSeason(showId: Long, seasonNumber: Int): Flow<List<EpisodeEntity>>

    @Query("SELECT * FROM episodes WHERE id = :id")
    suspend fun getById(id: Long): EpisodeEntity?

    @Query("SELECT * FROM episodes WHERE filePath = :filePath LIMIT 1")
    suspend fun getByFilePath(filePath: String): EpisodeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(episode: EpisodeEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<EpisodeEntity>)

    @Query("SELECT * FROM episodes WHERE showId = :showId ORDER BY seasonNumber, episodeNumber")
    fun getAllForShow(showId: Long): Flow<List<EpisodeEntity>>

    @Query("""
        SELECT * FROM episodes WHERE showId = :showId AND
        (seasonNumber > :season OR (seasonNumber = :season AND episodeNumber > :episode))
        ORDER BY seasonNumber ASC, episodeNumber ASC LIMIT 1
    """)
    suspend fun getNextEpisode(showId: Long, season: Int, episode: Int): EpisodeEntity?

    @Query("""
        SELECT * FROM episodes WHERE showId = :showId AND
        (seasonNumber < :season OR (seasonNumber = :season AND episodeNumber < :episode))
        ORDER BY seasonNumber DESC, episodeNumber DESC LIMIT 1
    """)
    suspend fun getPreviousEpisode(showId: Long, season: Int, episode: Int): EpisodeEntity?
}

