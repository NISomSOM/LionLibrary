package com.singam.lionlibrary.domain.repository

import com.singam.lionlibrary.domain.model.Episode
import com.singam.lionlibrary.domain.model.LibraryQuery
import com.singam.lionlibrary.domain.model.MediaFilter
import com.singam.lionlibrary.domain.model.MediaItem
import com.singam.lionlibrary.domain.model.MediaType
import com.singam.lionlibrary.domain.model.Season
import kotlinx.coroutines.flow.Flow

interface MediaRepository {
    fun browse(query: LibraryQuery): Flow<List<MediaItem>>

    suspend fun toggleWatchlist(id: Long)

    fun getByType(type: MediaType): Flow<List<MediaItem>>

    fun search(query: String, filter: MediaFilter): Flow<List<MediaItem>>

    fun getRecentlyAdded(): Flow<List<MediaItem>>

    suspend fun getById(id: Long): MediaItem?

    /** The row and its later changes (watchlist, ratings, artwork); null once it is removed. */
    fun observeById(id: Long): Flow<MediaItem?>

    fun getSeasonsForShow(showId: Long): Flow<List<Season>>

    fun getEpisodesForSeason(showId: Long, seasonNumber: Int): Flow<List<Episode>>

    fun getAllEpisodesForShow(showId: Long): Flow<List<Episode>>

    suspend fun getEpisodeById(episodeId: Long): Episode?
}
