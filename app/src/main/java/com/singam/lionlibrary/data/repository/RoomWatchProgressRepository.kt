package com.singam.lionlibrary.data.repository

import androidx.room.withTransaction
import com.singam.lionlibrary.data.local.db.MediaDatabase
import com.singam.lionlibrary.data.local.db.dao.EpisodeDao
import com.singam.lionlibrary.data.local.db.dao.MediaDao
import com.singam.lionlibrary.data.local.db.dao.WatchProgressDao
import com.singam.lionlibrary.data.local.db.entity.WatchProgressEntity
import com.singam.lionlibrary.data.mapper.toJumpBackInItem
import com.singam.lionlibrary.data.mapper.toWatchProgress
import com.singam.lionlibrary.domain.model.JumpBackInItem
import com.singam.lionlibrary.domain.model.WatchProgress
import com.singam.lionlibrary.domain.repository.WatchProgressRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomWatchProgressRepository(
    private val watchProgressDao: WatchProgressDao,
    private val episodeDao: EpisodeDao,
    private val mediaDao: MediaDao,
    private val database: MediaDatabase,
) : WatchProgressRepository {

    override fun getJumpBackInItems(): Flow<List<JumpBackInItem>> {
        return watchProgressDao.getJumpBackInItems().map { entities ->
            // One episode query per emission for every finished show, not one per show.
            val finishedShows =
                entities.filter { it.completed && it.mediaType != "MOVIE" }.map { it.mediaId }
            val episodesByShow =
                if (finishedShows.isEmpty()) emptyMap()
                else episodeDao.getForShows(finishedShows.distinct()).groupBy { it.showId }
            entities.mapNotNull { entity ->
                if (entity.completed && entity.mediaType != "MOVIE") {
                    val nextEp =
                        nextEpisodeAfter(
                            episodesByShow[entity.mediaId].orEmpty(),
                            entity.seasonNumber ?: 0,
                            entity.episodeEndNumber ?: entity.episodeNumber ?: 0,
                        )
                    if (nextEp != null) {
                        entity
                            .toJumpBackInItem()
                            .copy(
                                episodeId = nextEp.id,
                                episodeTitle = nextEp.title,
                                seasonNumber = nextEp.seasonNumber,
                                episodeNumber = nextEp.episodeNumber,
                                thumbnailPath = nextEp.thumbnailPath,
                                filePath = nextEp.filePath,
                                progress = 0f,
                                isNextUp = true,
                                durationMs = 0L,
                                positionMs = 0L,
                            )
                    } else {
                        // Series done
                        null
                    }
                } else if (!entity.completed) {
                    entity.toJumpBackInItem().copy(isNextUp = false)
                } else {
                    // Movie done
                    null
                }
            }
        }
    }

    override suspend fun getProgress(mediaId: Long, episodeId: Long): WatchProgress? {
        return watchProgressDao.getProgress(mediaId, episodeId)?.toWatchProgress()
    }

    override fun getProgressForMedia(mediaId: Long): Flow<List<WatchProgress>> {
        return watchProgressDao.getProgressForMedia(mediaId).map { entities ->
            entities.map { it.toWatchProgress() }
        }
    }

    override suspend fun markAsStarted(mediaId: Long, episodeId: Long) =
        writeIfTargetExists(mediaId, episodeId) {
            val existing = watchProgressDao.getProgress(mediaId, episodeId)
            if (existing?.completed == true) return@writeIfTargetExists // Already watched.
            // Preserve an existing resume point rather than resetting it.
            watchProgressDao.upsert(
                WatchProgressEntity(
                    mediaId = mediaId,
                    episodeId = episodeId,
                    progress = existing?.progress ?: 0.01f,
                    lastPositionMs = existing?.lastPositionMs ?: 0L,
                    durationMs = existing?.durationMs ?: 0L,
                    lastWatched = System.currentTimeMillis(),
                    completed = false,
                    isHidden = false,
                )
            )
        }

    override suspend fun markAsWatched(mediaId: Long, episodeId: Long) =
        writeIfTargetExists(mediaId, episodeId) {
            val existing = watchProgressDao.getProgress(mediaId, episodeId)
            watchProgressDao.upsert(
                WatchProgressEntity(
                    mediaId = mediaId,
                    episodeId = episodeId,
                    progress = 1.0f,
                    lastPositionMs = existing?.lastPositionMs ?: 0L,
                    durationMs = existing?.durationMs ?: 0L,
                    lastWatched = System.currentTimeMillis(),
                    completed = true,
                    isHidden = false,
                )
            )
        }

    override suspend fun markAllAsWatched(mediaId: Long, episodeIds: List<Long>) {
        watchProgressDao.markAllWatched(mediaId, episodeIds)
    }

    override suspend fun markAsUnwatched(mediaId: Long, episodeId: Long) =
        writeIfTargetExists(mediaId, episodeId) {
            watchProgressDao.upsert(
                WatchProgressEntity(
                    mediaId = mediaId,
                    episodeId = episodeId,
                    progress = 0f,
                    lastPositionMs = 0L,
                    durationMs = 0L,
                    lastWatched = System.currentTimeMillis(),
                    completed = false,
                    isHidden = false,
                )
            )
        }

    /**
     * Same guard as `RoomPlaybackRepository`: a history row is written only while its media row
     * exists and, for episodes, the episode still belongs to that show. A deleted title cannot be
     * recreated in history by a late menu action.
     */
    private suspend fun writeIfTargetExists(
        mediaId: Long,
        episodeId: Long,
        write: suspend () -> Unit,
    ) {
        database.withTransaction {
            if (mediaDao.getById(mediaId) == null) return@withTransaction
            if (episodeId != 0L && episodeDao.getById(episodeId)?.showId != mediaId)
                return@withTransaction
            write()
        }
    }

    override suspend fun deleteProgress(mediaId: Long, episodeId: Long) {
        watchProgressDao.deleteProgress(mediaId, episodeId)
    }

    override suspend fun deleteProgressForMedia(mediaId: Long) {
        watchProgressDao.deleteProgressForMedia(mediaId)
    }

    override suspend fun hideMediaFromJumpBackIn(mediaId: Long) {
        watchProgressDao.hideMediaFromJumpBackIn(mediaId)
    }

    override suspend fun clearAll() {
        watchProgressDao.clearAll()
    }
}
