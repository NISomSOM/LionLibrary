package com.singam.lionlibrary.domain.usecase

import com.singam.lionlibrary.domain.model.MediaFilter
import com.singam.lionlibrary.domain.model.MediaItem
import com.singam.lionlibrary.domain.repository.MediaRepository
import kotlinx.coroutines.flow.Flow

class SearchMediaUseCase(
    private val mediaRepository: MediaRepository
) {
    operator fun invoke(query: String, filter: MediaFilter): Flow<List<MediaItem>> {
        return mediaRepository.search(query, filter)
    }
}

