package com.singam.lionlibrary.domain.usecase

import com.singam.lionlibrary.domain.model.HomeContent
import com.singam.lionlibrary.domain.model.MediaItem
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

                val allMedia = (movies + tvShows + anime).distinctBy { it.id }
                HomeContent(
                    jumpBackInItems = jumpBackInItems,
                    // Category rows are alphabetical (Recently added and Jump back in keep their
                    // own recency order).
                    movies = movies.sortedByTitle(),
                    tvShows = tvShows.sortedByTitle(),
                    anime = (anime + animeMovies).sortedByTitle(),
                    recentlyAdded = recentlyAdded,
                    allMedia = allMedia,
                    genres = genreShelves(allMedia),
                )
            }
            .flowOn(dispatcher)
    }
}

/** Alphabetical by title, ignoring case; equal titles keep a stable order by local id. */
internal fun List<MediaItem>.sortedByTitle(): List<MediaItem> =
    sortedWith(
        compareBy<MediaItem, String>(String.CASE_INSENSITIVE_ORDER) { it.title }.thenBy { it.id }
    )

/**
 * Groups titles by their comma-separated genres (trimmed, blanks dropped), shelves sorted by name
 * and the titles in each shelf alphabetical.
 */
internal fun genreShelves(items: List<MediaItem>): Map<String, List<MediaItem>> {
    val shelves = sortedMapOf<String, MutableList<MediaItem>>()
    items.forEach { item ->
        item.genres
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.distinct()
            ?.forEach { genre -> shelves.getOrPut(genre) { mutableListOf() }.add(item) }
    }
    return shelves.mapValuesTo(sortedMapOf()) { (_, titles) -> titles.sortedByTitle() }
}
