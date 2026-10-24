package com.singam.lionlibrary.data.repository

import androidx.room.withTransaction
import com.singam.lionlibrary.data.local.db.dao.*
import com.singam.lionlibrary.data.local.db.entity.*
import com.singam.lionlibrary.data.mapper.toWatchProgress
import com.singam.lionlibrary.domain.model.*
import com.singam.lionlibrary.domain.repository.PlaybackRepository
import com.singam.lionlibrary.domain.usecase.OrderedProgressWriter
import kotlinx.coroutines.*

class RoomPlaybackRepository(
    private val media: MediaDao,
    private val episodes: EpisodeDao,
    private val history: WatchProgressDao,
    private val database: com.singam.lionlibrary.data.local.db.MediaDatabase,
) : PlaybackRepository {
    private val writer =
        OrderedProgressWriter(CoroutineScope(SupervisorJob() + Dispatchers.IO)) { snapshot ->
            val value = snapshot.toProgress()
            database.withTransaction {
                if (
                    media.getById(value.mediaId) == null ||
                        (value.episodeId != 0L &&
                            episodes.getById(value.episodeId)?.showId != value.mediaId)
                )
                    return@withTransaction
                history.upsert(
                    WatchProgressEntity(
                        value.mediaId,
                        value.episodeId,
                        value.progress,
                        value.lastPositionMs,
                        value.durationMs,
                        value.lastWatched,
                        value.completed,
                    )
                )
            }
            OperationResult.Success(Unit)
        }

    override suspend fun target(type: MediaType, id: Long): PlaybackTarget? {
        if (type == MediaType.MOVIE) {
            val item = media.getById(id) ?: return null
            return PlaybackTarget(
                item.id,
                0,
                MediaType.MOVIE,
                item.title,
                item.filePath,
                item.externalSubtitlePath,
                item.preferredEngine,
                year = item.year,
                logoPath = item.logoPath,
                overview = item.overview?.takeIf { it.isNotBlank() },
                preferJapaneseAudio =
                    item.genres?.split(',')?.any { it.trim().equals("Anime", ignoreCase = true) } ==
                        true,
                // Legacy raw rows stored as TV/anime also use this route; a show id is not a movie
                // id.
                tmdbId =
                    item.tmdbId.takeIf {
                        !item.isUnidentified && item.mediaType == MediaType.MOVIE.name
                    },
            )
        }
        val ep = episodes.getById(id) ?: return null
        return episodeTarget(ep)
    }

    private suspend fun episodeTarget(ep: EpisodeEntity): PlaybackTarget {
        val show = media.getById(ep.showId)
        return PlaybackTarget(
            ep.showId,
            ep.id,
            show?.mediaType?.let { MediaType.valueOf(it) } ?: MediaType.TV_SHOW,
            show?.title ?: ep.title.orEmpty(),
            ep.filePath,
            ep.externalSubtitlePath,
            ep.preferredEngine,
            season = ep.seasonNumber,
            episode = ep.episodeNumber,
            episodeEnd = ep.episodeEndNumber,
            episodeTitle = ep.title,
            logoPath = show?.logoPath,
            overview =
                ep.overview?.takeIf { it.isNotBlank() }
                    ?: show?.overview?.takeIf { it.isNotBlank() },
            tmdbId = show?.takeIf { !it.isUnidentified }?.tmdbId,
        )
    }

    override suspend fun adjacent(target: PlaybackTarget, next: Boolean): PlaybackTarget? {
        if (target.episodeId == 0L) return null
        val episode =
            if (next)
                episodes.getNextEpisode(
                    target.mediaId,
                    target.season ?: 0,
                    target.episodeEnd ?: target.episode ?: 0,
                )
            else
                episodes.getPreviousEpisode(target.mediaId, target.season ?: 0, target.episode ?: 0)
        return episode?.let { episodeTarget(it) }
    }

    override suspend fun progress(target: PlaybackTarget) =
        history.getProgress(target.mediaId, target.episodeId)?.toWatchProgress()

    override fun save(snapshot: PlaybackSnapshot) = writer.submit(snapshot)
}
