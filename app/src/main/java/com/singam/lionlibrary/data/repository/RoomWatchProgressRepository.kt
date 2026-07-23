package com.singam.lionlibrary.data.repository

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
    private val episodeDao: com.singam.lionlibrary.data.local.db.dao.EpisodeDao
) : WatchProgressRepository {

    override fun getJumpBackInItems(): Flow<List<JumpBackInItem>> {
        return watchProgressDao.getJumpBackInItems().map { entities ->
            entities.mapNotNull { entity ->
                if (entity.completed && entity.mediaType != "MOVIE") {
                    val nextEp = episodeDao.getNextEpisode(entity.mediaId, entity.seasonNumber ?: 0, entity.episodeNumber ?: 0)
                    if (nextEp != null) {
                        entity.toJumpBackInItem().copy(
                            episodeId = nextEp.id,
                            episodeTitle = nextEp.title,
                            seasonNumber = nextEp.seasonNumber,
                            episodeNumber = nextEp.episodeNumber,
                            thumbnailPath = nextEp.thumbnailPath,
                            filePath = nextEp.filePath,
                            progress = 0f,
                            isNextUp = true
                        )
                    } else {
                        // Series completed, hide from jump back in
                        null
                    }
                } else if (!entity.completed) {
                    entity.toJumpBackInItem().copy(isNextUp = false)
                } else {
                    // Movie completed
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

    override suspend fun markAsStarted(mediaId: Long, episodeId: Long) {
        val existing = watchProgressDao.getProgress(mediaId, episodeId)
        if (existing?.completed == true) return // Don't override if already watched
        
        watchProgressDao.upsert(
            WatchProgressEntity(
                mediaId = mediaId,
                episodeId = episodeId,
                progress = 0.01f,
                lastPositionMs = existing?.lastPositionMs ?: 0L,
                durationMs = existing?.durationMs ?: 0L,
                lastWatched = System.currentTimeMillis(),
                completed = false,
                isHidden = false
            )
        )
    }

    override suspend fun markAsWatched(mediaId: Long, episodeId: Long) {
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
                isHidden = false
            )
        )
    }

    override suspend fun markAsUnwatched(mediaId: Long, episodeId: Long) {
        watchProgressDao.upsert(
            WatchProgressEntity(
                mediaId = mediaId,
                episodeId = episodeId,
                progress = 0f,
                lastPositionMs = 0L,
                durationMs = 0L,
                lastWatched = System.currentTimeMillis(),
                completed = false,
                isHidden = false
            )
        )
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

