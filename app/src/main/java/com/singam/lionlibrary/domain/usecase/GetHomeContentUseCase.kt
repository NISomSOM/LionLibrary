package com.singam.lionlibrary.domain.usecase

import com.singam.lionlibrary.domain.model.HomeContent
import com.singam.lionlibrary.domain.model.MediaType
import com.singam.lionlibrary.domain.repository.MediaRepository
import com.singam.lionlibrary.domain.repository.WatchProgressRepository
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
            val animeMovies = movies.filter { it.genres?.contains("Anime", ignoreCase = true) == true }
            val pureMovies = movies.filter { it.genres?.contains("Anime", ignoreCase = true) != true }

            HomeContent(
                jumpBackInItems = jumpBackInItems,
                movies = pureMovies,
                tvShows = tvShows,
                anime = anime + animeMovies,
                recentlyAdded = recentlyAdded
            )
        }
    }
}

