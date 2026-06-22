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
    private val watchProgressDao: WatchProgressDao
) : WatchProgressRepository {

    override fun getJumpBackInItems(): Flow<List<JumpBackInItem>> {
        return watchProgressDao.getJumpBackInItems().map { entities ->
            entities.map { it.toJumpBackInItem() }
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
                completed = false
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
                completed = true
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
                completed = false
            )
        )
    }

    override suspend fun clearAll() {
        watchProgressDao.clearAll()
    }
}

