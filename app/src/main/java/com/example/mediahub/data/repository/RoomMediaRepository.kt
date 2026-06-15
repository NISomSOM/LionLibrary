package com.example.mediahub.data.repository

import com.example.mediahub.data.local.db.dao.EpisodeDao
import com.example.mediahub.data.local.db.dao.MediaDao
import com.example.mediahub.data.local.db.dao.SeasonDao
import com.example.mediahub.data.mapper.toEpisode
import com.example.mediahub.data.mapper.toEpisodeEntity
import com.example.mediahub.data.mapper.toMediaEntity
import com.example.mediahub.data.mapper.toMediaItem
import com.example.mediahub.data.mapper.toSeason
import com.example.mediahub.data.mapper.toSeasonEntity
import com.example.mediahub.domain.model.Episode
import com.example.mediahub.domain.model.MediaFilter
import com.example.mediahub.domain.model.MediaItem
import com.example.mediahub.domain.model.MediaType
import com.example.mediahub.domain.model.Season
import com.example.mediahub.domain.repository.MediaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomMediaRepository(
    private val mediaDao: MediaDao,
    private val seasonDao: SeasonDao,
    private val episodeDao: EpisodeDao
) : MediaRepository {

    override fun getByType(type: MediaType): Flow<List<MediaItem>> {
        return mediaDao.getByType(type.name).map { entities ->
            entities.map { it.toMediaItem() }
        }
    }

    override fun search(query: String, filter: MediaFilter): Flow<List<MediaItem>> {
        return mediaDao.search(query).map { entities ->
            val filtered = when (filter) {
                MediaFilter.ALL -> entities
                MediaFilter.MOVIES -> entities.filter { it.mediaType == MediaType.MOVIE.name }
                MediaFilter.TV_SHOWS -> entities.filter { it.mediaType == MediaType.TV_SHOW.name }
                MediaFilter.ANIME -> entities.filter { it.mediaType == MediaType.ANIME.name }
            }
            filtered.map { it.toMediaItem() }
        }
    }

    override fun getRecentlyAdded(): Flow<List<MediaItem>> {
        return mediaDao.getRecentlyAdded().map { entities ->
            entities.map { it.toMediaItem() }
        }
    }

    override suspend fun getById(id: Long): MediaItem? {
        return mediaDao.getById(id)?.toMediaItem()
    }

    override suspend fun insert(media: MediaItem): Long {
        return mediaDao.insert(media.toMediaEntity())
    }

    override suspend fun getByTmdbId(tmdbId: Int): MediaItem? {
        return mediaDao.getByTmdbId(tmdbId)?.toMediaItem()
    }

    override fun getSeasonsForShow(showId: Long): Flow<List<Season>> {
        return seasonDao.getSeasonsForShow(showId).map { entities ->
            entities.map { it.toSeason() }
        }
    }

    override fun getEpisodesForSeason(showId: Long, seasonNumber: Int): Flow<List<Episode>> {
        return episodeDao.getEpisodesForSeason(showId, seasonNumber).map { entities ->
            entities.map { it.toEpisode() }
        }
    }

    override fun getAllEpisodesForShow(showId: Long): Flow<List<Episode>> {
        return episodeDao.getAllForShow(showId).map { entities ->
            entities.map { it.toEpisode() }
        }
    }

    override suspend fun getEpisodeById(episodeId: Long): Episode? {
        return episodeDao.getById(episodeId)?.toEpisode()
    }

    override suspend fun insertSeason(season: Season): Long {
        return seasonDao.insert(season.toSeasonEntity())
    }

    override suspend fun insertEpisode(episode: Episode): Long {
        return episodeDao.insert(episode.toEpisodeEntity())
    }

    override suspend fun getEpisodeByFilePath(filePath: String): Episode? {
        return episodeDao.getByFilePath(filePath)?.toEpisode()
    }

    override suspend fun getByFilePath(filePath: String): MediaItem? {
        return mediaDao.getByFilePath(filePath)?.toMediaItem()
    }
}
