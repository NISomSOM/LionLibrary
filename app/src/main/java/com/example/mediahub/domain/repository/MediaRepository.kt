package com.example.mediahub.domain.repository

import com.example.mediahub.domain.model.Episode
import com.example.mediahub.domain.model.MediaFilter
import com.example.mediahub.domain.model.MediaItem
import com.example.mediahub.domain.model.MediaType
import com.example.mediahub.domain.model.Season
import kotlinx.coroutines.flow.Flow

interface MediaRepository {

    fun getByType(type: MediaType): Flow<List<MediaItem>>

    fun search(query: String, filter: MediaFilter): Flow<List<MediaItem>>

    fun getRecentlyAdded(): Flow<List<MediaItem>>

    suspend fun getById(id: Long): MediaItem?

    suspend fun insert(media: MediaItem): Long

    suspend fun getByTmdbId(tmdbId: Int): MediaItem?

    fun getSeasonsForShow(showId: Long): Flow<List<Season>>

    fun getEpisodesForSeason(showId: Long, seasonNumber: Int): Flow<List<Episode>>

    fun getAllEpisodesForShow(showId: Long): Flow<List<Episode>>

    suspend fun getEpisodeById(episodeId: Long): Episode?

    suspend fun insertSeason(season: Season): Long

    suspend fun insertEpisode(episode: Episode): Long

    suspend fun getEpisodeByFilePath(filePath: String): Episode?

    suspend fun getByFilePath(filePath: String): MediaItem?
}
