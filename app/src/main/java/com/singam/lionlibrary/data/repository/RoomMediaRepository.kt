package com.singam.lionlibrary.data.repository

import com.singam.lionlibrary.data.local.db.dao.EpisodeDao
import com.singam.lionlibrary.data.local.db.dao.MediaDao
import com.singam.lionlibrary.data.local.db.dao.SeasonDao
import com.singam.lionlibrary.data.mapper.toEpisode
import com.singam.lionlibrary.data.mapper.toMediaItem
import com.singam.lionlibrary.data.mapper.toSeason
import com.singam.lionlibrary.domain.model.Episode
import com.singam.lionlibrary.domain.model.MediaFilter
import com.singam.lionlibrary.domain.model.MediaItem
import com.singam.lionlibrary.domain.model.MediaType
import com.singam.lionlibrary.domain.model.Season
import com.singam.lionlibrary.domain.repository.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

class RoomMediaRepository(
    private val mediaDao: MediaDao,
    private val seasonDao: SeasonDao,
    private val episodeDao: EpisodeDao,
) : MediaRepository {
    override fun browse(
        query: com.singam.lionlibrary.domain.model.LibraryQuery
    ): Flow<List<MediaItem>> {
        val escaped =
            query.text.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        return mediaDao
            .browse(
                query.text.trim(),
                "%$escaped%",
                query.type.name,
                query.genre,
                query.year,
                query.watch.name,
                query.sort.name,
                query.watchlistOnly,
            )
            .map { items -> items.map { it.toMediaItem() } }
            .flowOn(Dispatchers.Default)
    }

    override suspend fun toggleWatchlist(id: Long) = mediaDao.toggleWatchlist(id)

    override fun getByType(type: MediaType): Flow<List<MediaItem>> {
        return mediaDao.getByType(type.name).map { entities -> entities.map { it.toMediaItem() } }
    }

    override fun search(query: String, filter: MediaFilter): Flow<List<MediaItem>> =
        browse(com.singam.lionlibrary.domain.model.LibraryQuery(text = query, type = filter))

    override fun getRecentlyAdded(): Flow<List<MediaItem>> {
        return mediaDao.getRecentlyAdded().map { entities -> entities.map { it.toMediaItem() } }
    }

    override suspend fun getById(id: Long): MediaItem? {
        return mediaDao.getById(id)?.toMediaItem()
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
        return episodeDao.getAllForShow(showId).map { entities -> entities.map { it.toEpisode() } }
    }

    override suspend fun getEpisodeById(episodeId: Long): Episode? {
        return episodeDao.getById(episodeId)?.toEpisode()
    }
}
