package com.singam.lionlibrary.data.local.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.singam.lionlibrary.data.local.db.entity.MediaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {
    @Query("SELECT * FROM media ORDER BY title") fun getAll(): Flow<List<MediaEntity>>

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
        """
    )
    fun search(query: String): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media WHERE id = :id") suspend fun getById(id: Long): MediaEntity?

    @Query("SELECT * FROM media WHERE tmdbId = :tmdbId LIMIT 1")
    suspend fun getByTmdbId(tmdbId: Int): MediaEntity?

    @Query("SELECT * FROM media WHERE tmdbId = :tmdbId AND mediaType != 'MOVIE' LIMIT 1")
    suspend fun getTvByTmdbId(tmdbId: Int): MediaEntity?

    @Update suspend fun update(media: MediaEntity)

    @Query(
        "UPDATE media SET posterPath=COALESCE(:poster,posterPath), backdropPath=COALESCE(:backdrop,backdropPath), logoPath=COALESCE(:logo,logoPath) WHERE id=:id"
    )
    suspend fun updateArtwork(id: Long, poster: String?, backdrop: String?, logo: String?)

    @Query("UPDATE media SET isWatchlisted = NOT isWatchlisted WHERE id = :id")
    suspend fun toggleWatchlist(id: Long)

    @Query("SELECT * FROM media WHERE isUnidentified = 1 ORDER BY sourceShowTitle, title")
    fun getUnidentified(): Flow<List<MediaEntity>>

    @Query("DELETE FROM media WHERE id = :id") suspend fun deleteById(id: Long)

    @Query("SELECT * FROM media WHERE sourceShowTitle = :title AND isUnidentified = 1")
    suspend fun getUnidentifiedShow(title: String): List<MediaEntity>

    @Query(
        """
        SELECT m.* FROM media m WHERE (m.isUnidentified = 0 OR m.filePath IS NOT NULL)
        AND (:text = '' OR m.title LIKE :pattern ESCAPE '\' OR m.originalTitle LIKE :pattern ESCAPE '\'
             OR m.overview LIKE :pattern ESCAPE '\' OR m.genres LIKE :pattern ESCAPE '\')
        AND (:type = 'ALL' OR (:type = 'MOVIES' AND m.mediaType = 'MOVIE')
             OR (:type = 'TV_SHOWS' AND m.mediaType = 'TV_SHOW')
             OR (:type = 'ANIME' AND (m.mediaType = 'ANIME' OR (m.mediaType = 'MOVIE' AND m.genres LIKE '%Anime%'))))
        AND (:genre = '' OR instr(',' || lower(m.genres) || ',', ',' || lower(:genre) || ',') > 0)
        AND (:year IS NULL OR m.year = :year)
        AND (:watchlist = 0 OR m.isWatchlisted = 1)
        AND (:watch = 'ALL'
             OR (:watch = 'WATCHED' AND ((m.mediaType = 'MOVIE' OR m.isUnidentified = 1)
                 AND EXISTS (SELECT 1 FROM watch_progress w WHERE w.mediaId=m.id AND w.episodeId=0 AND w.completed=1)
                 OR m.mediaType != 'MOVIE' AND m.isUnidentified = 0 AND EXISTS (SELECT 1 FROM episodes e WHERE e.showId=m.id)
                 AND NOT EXISTS (SELECT 1 FROM episodes e WHERE e.showId=m.id AND NOT EXISTS
                    (SELECT 1 FROM watch_progress w WHERE w.mediaId=m.id AND w.episodeId=e.id AND w.completed=1))))
             OR (:watch = 'UNWATCHED' AND NOT EXISTS (SELECT 1 FROM watch_progress w WHERE w.mediaId=m.id AND (w.completed=1 OR w.lastPositionMs>0)))
             OR (:watch = 'IN_PROGRESS' AND (
                EXISTS (SELECT 1 FROM watch_progress w WHERE w.mediaId=m.id AND w.completed=0 AND w.lastPositionMs>0)
                OR m.mediaType != 'MOVIE' AND m.isUnidentified = 0 AND EXISTS (SELECT 1 FROM watch_progress w WHERE w.mediaId=m.id AND w.completed=1)
                AND EXISTS (SELECT 1 FROM episodes e WHERE e.showId=m.id AND NOT EXISTS
                    (SELECT 1 FROM watch_progress w WHERE w.mediaId=m.id AND w.episodeId=e.id AND w.completed=1)))))
        ORDER BY CASE WHEN :sort = 'TITLE_ASC' THEN m.title END COLLATE NOCASE ASC,
                 CASE WHEN :sort = 'TITLE_DESC' THEN m.title END COLLATE NOCASE DESC,
                 CASE WHEN :sort = 'RECENT' THEN COALESCE(NULLIF(m.addedAt, 0),m.lastUpdated) END DESC,
                 CASE WHEN :sort = 'YEAR' THEN m.year END DESC,
                 CASE WHEN :sort = 'RATING' THEN m.rating END DESC,
                 m.title COLLATE NOCASE ASC, m.id ASC
    """
    )
    fun browse(
        text: String,
        pattern: String,
        type: String,
        genre: String,
        year: Int?,
        watch: String,
        sort: String,
        watchlist: Boolean,
    ): Flow<List<MediaEntity>>

    @Upsert suspend fun insert(media: MediaEntity): Long

    @Upsert suspend fun insertAll(items: List<MediaEntity>)

    @Query(
        "SELECT * FROM media WHERE isUnidentified = 0 ORDER BY COALESCE(NULLIF(addedAt,0),lastUpdated) DESC, id ASC LIMIT 20"
    )
    fun getRecentlyAdded(): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media WHERE filePath = :filePath LIMIT 1")
    suspend fun getByFilePath(filePath: String): MediaEntity?

    @Query(
        "UPDATE media SET letterboxdRating = :rating, letterboxdUpdatedAt = :updatedAt WHERE id = :id"
    )
    suspend fun updateLetterboxdRating(id: Long, rating: Float?, updatedAt: Long)
}
