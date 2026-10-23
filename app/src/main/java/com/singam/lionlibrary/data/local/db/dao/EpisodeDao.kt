package com.singam.lionlibrary.data.local.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.singam.lionlibrary.data.local.db.entity.EpisodeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EpisodeDao {
    @Query("DELETE FROM episodes WHERE id=:id") suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM episodes WHERE showId=:showId")
    suspend fun countForShow(showId: Long): Int

    @Query("UPDATE episodes SET thumbnailPath=:path WHERE id=:id")
    suspend fun updateArtwork(id: Long, path: String)

    @Query(
        "UPDATE episodes SET episodeEndNumber=:end WHERE filePath=:path AND episodeEndNumber IS NULL"
    )
    suspend fun updateRange(path: String, end: Int)

    @Query("SELECT * FROM episodes") suspend fun getAll(): List<EpisodeEntity>

    @Query(
        """
        SELECT * FROM episodes 
        WHERE showId = :showId AND seasonNumber = :seasonNumber 
        ORDER BY episodeNumber ASC
        """
    )
    fun getEpisodesForSeason(showId: Long, seasonNumber: Int): Flow<List<EpisodeEntity>>

    @Query("SELECT * FROM episodes WHERE id = :id") suspend fun getById(id: Long): EpisodeEntity?

    @Query("SELECT * FROM episodes WHERE filePath = :filePath LIMIT 1")
    suspend fun getByFilePath(filePath: String): EpisodeEntity?

    @Upsert suspend fun insert(episode: EpisodeEntity): Long

    @Upsert suspend fun insertAll(items: List<EpisodeEntity>)

    @Query("SELECT * FROM episodes WHERE showId = :showId ORDER BY seasonNumber, episodeNumber")
    fun getAllForShow(showId: Long): Flow<List<EpisodeEntity>>

    @Query(
        """
        SELECT * FROM episodes WHERE showId = :showId AND
        (seasonNumber > :season OR (seasonNumber = :season AND episodeNumber > :episode))
        ORDER BY seasonNumber ASC, episodeNumber ASC LIMIT 1
    """
    )
    suspend fun getNextEpisode(showId: Long, season: Int, episode: Int): EpisodeEntity?

    @Query(
        """
        SELECT * FROM episodes WHERE showId = :showId AND
        (seasonNumber < :season OR (seasonNumber = :season AND episodeNumber < :episode))
        ORDER BY seasonNumber DESC, episodeNumber DESC LIMIT 1
    """
    )
    suspend fun getPreviousEpisode(showId: Long, season: Int, episode: Int): EpisodeEntity?
}
