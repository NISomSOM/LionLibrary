package com.singam.lionlibrary.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.singam.lionlibrary.data.local.db.entity.SeasonEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SeasonDao {

    @Query("SELECT * FROM seasons WHERE showId = :showId ORDER BY seasonNumber ASC")
    fun getSeasonsForShow(showId: Long): Flow<List<SeasonEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(season: SeasonEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<SeasonEntity>)

    @Query("SELECT * FROM seasons WHERE showId = :showId AND seasonNumber = :seasonNumber LIMIT 1")
    suspend fun getByShowAndSeason(showId: Long, seasonNumber: Int): SeasonEntity?
}

