package com.singam.lionlibrary.domain.usecase

import com.singam.lionlibrary.domain.model.HomeContent
import com.singam.lionlibrary.domain.model.MediaType
import com.singam.lionlibrary.domain.repository.MediaRepository
import com.singam.lionlibrary.domain.repository.WatchProgressRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn

// Dispatcher is injected because this unit-tested class leaves the main thread.
class GetHomeContentUseCase(
    private val mediaRepository: MediaRepository,
    private val watchProgressRepository: WatchProgressRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    // Room re-emits on any table write; identical results must not rebuild Home.
    // Mapping and combining run off the main thread.
    operator fun invoke(): Flow<HomeContent> {
        return combine(
                mediaRepository.getByType(MediaType.MOVIE).distinctUntilChanged(),
                mediaRepository.getByType(MediaType.TV_SHOW).distinctUntilChanged(),
                mediaRepository.getByType(MediaType.ANIME).distinctUntilChanged(),
                mediaRepository.getRecentlyAdded().distinctUntilChanged(),
                watchProgressRepository.getJumpBackInItems().distinctUntilChanged(),
            ) { movies, tvShows, anime, recentlyAdded, jumpBackInItems ->
                val animeMovies = movies.filter {
                    it.genres?.contains("Anime", ignoreCase = true) == true
                }

                HomeContent(
                    jumpBackInItems = jumpBackInItems,
                    movies = movies,
                    tvShows = tvShows,
                    anime = anime + animeMovies,
                    recentlyAdded = recentlyAdded,
                )
            }
            .flowOn(dispatcher)
    }
}
