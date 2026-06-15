package com.example.mediahub.domain.usecase

import com.example.mediahub.domain.model.MediaFilter
import com.example.mediahub.domain.model.MediaItem
import com.example.mediahub.domain.repository.MediaRepository
import kotlinx.coroutines.flow.Flow

class SearchMediaUseCase(
    private val mediaRepository: MediaRepository
) {
    operator fun invoke(query: String, filter: MediaFilter): Flow<List<MediaItem>> {
        return mediaRepository.search(query, filter)
    }
}
