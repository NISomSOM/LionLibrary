package com.singam.lionlibrary.domain.repository

import com.singam.lionlibrary.domain.model.JumpBackInItem
import com.singam.lionlibrary.domain.model.WatchProgress
import kotlinx.coroutines.flow.Flow

interface WatchProgressRepository {

    fun getJumpBackInItems(): Flow<List<JumpBackInItem>>

    suspend fun getProgress(mediaId: Long, episodeId: Long): WatchProgress?

    fun getProgressForMedia(mediaId: Long): Flow<List<WatchProgress>>

    suspend fun markAsStarted(mediaId: Long, episodeId: Long)

    suspend fun markAsWatched(mediaId: Long, episodeId: Long)

    suspend fun markAsUnwatched(mediaId: Long, episodeId: Long)

    suspend fun clearAll()
}

