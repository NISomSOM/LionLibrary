package com.example.mediahub.domain.usecase

import com.example.mediahub.domain.model.HomeContent
import com.example.mediahub.domain.model.MediaType
import com.example.mediahub.domain.repository.MediaRepository
import com.example.mediahub.domain.repository.WatchProgressRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class GetHomeContentUseCase(
    private val mediaRepository: MediaRepository,
    private val watchProgressRepository: WatchProgressRepository
) {
    operator fun invoke(): Flow<HomeContent> {
        return combine(
            mediaRepository.getByType(MediaType.MOVIE),
            mediaRepository.getByType(MediaType.TV_SHOW),
            mediaRepository.getByType(MediaType.ANIME),
            mediaRepository.getRecentlyAdded(),
            watchProgressRepository.getJumpBackInItems()
        ) { movies, tvShows, anime, recentlyAdded, jumpBackInItems ->
            HomeContent(
                jumpBackInItems = jumpBackInItems,
                movies = movies,
                tvShows = tvShows,
                anime = anime,
                recentlyAdded = recentlyAdded
            )
        }
    }
}
