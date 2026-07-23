package com.singam.lionlibrary.domain.usecase

import com.singam.lionlibrary.domain.repository.WatchProgressRepository

class UpdateWatchProgressUseCase(
    private val watchProgressRepository: WatchProgressRepository
) {
    suspend fun markAsStarted(mediaId: Long, episodeId: Long) {
        watchProgressRepository.markAsStarted(mediaId, episodeId)
    }

    suspend fun markAsWatched(mediaId: Long, episodeId: Long) {
        watchProgressRepository.markAsWatched(mediaId, episodeId)
    }

    suspend fun markAsUnwatched(mediaId: Long, episodeId: Long) {
        watchProgressRepository.markAsUnwatched(mediaId, episodeId)
    }

    suspend fun removeWatchProgress(mediaId: Long, episodeId: Long) {
        watchProgressRepository.deleteProgress(mediaId, episodeId)
        watchProgressRepository.hideMediaFromJumpBackIn(mediaId)
    }
}

