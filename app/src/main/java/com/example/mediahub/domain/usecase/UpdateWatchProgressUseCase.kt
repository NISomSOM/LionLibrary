package com.example.mediahub.domain.usecase

import com.example.mediahub.domain.repository.WatchProgressRepository

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
}
