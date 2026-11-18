package com.singam.lionlibrary.presentation

import assertk.assertThat
import assertk.assertions.*
import com.singam.lionlibrary.data.mapper.libraryGenres
import com.singam.lionlibrary.data.remote.dto.*
import com.singam.lionlibrary.domain.model.*
import com.singam.lionlibrary.domain.repository.WatchProgressRepository
import com.singam.lionlibrary.domain.usecase.GetHomeContentUseCase
import com.singam.lionlibrary.testing.FakeMediaRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class HomeContentTest {
    // Break vectors: anime movie excluded from Movies, series turned into a movie,
    // animation alone treated as anime, no genres, and mismatched original language/country.
    @Test
    fun `anime film appears in both categories while retaining movie identity`() = runTest {
        val film =
            MediaItem(
                id = 7,
                tmdbId = 99,
                title = "Anime film",
                originalTitle = null,
                overview = null,
                posterPath = null,
                backdropPath = null,
                genres = "Animation,Anime",
                rating = null,
                year = 2023,
                mediaType = MediaType.MOVIE,
                matchConfidence = 1f,
                isUnidentified = false,
                lastUpdated = 1,
                filePath = "content://fixture/Movies/film.mkv",
            )
        val series =
            film.copy(id = 8, title = "Series", mediaType = MediaType.ANIME, filePath = null)
        val repo =
            FakeMediaRepository().apply {
                byType = mapOf(MediaType.MOVIE to listOf(film), MediaType.ANIME to listOf(series))
            }
        val content =
            GetHomeContentUseCase(repo, EmptyHistory(), UnconfinedTestDispatcher(testScheduler))
                .invoke()
                .first()
        assertThat(content.movies.map { it.id }).isEqualTo(listOf(7L))
        // Alphabetical: "Anime film" comes before "Series" though it was added from Movies.
        assertThat(content.anime.map { it.id }).isEqualTo(listOf(7L, 8L))
        assertThat(content.anime.first().mediaType).isEqualTo(MediaType.MOVIE)
        assertThat(content.anime.first().filePath).isEqualTo(film.filePath)
    }

    @Test
    fun `category rows are alphabetical ignoring case while Recently added keeps its order`() =
        runTest {
            fun item(id: Long, title: String, type: MediaType = MediaType.MOVIE) =
                MediaItem(
                    id = id,
                    tmdbId = id.toInt(),
                    title = title,
                    originalTitle = null,
                    overview = null,
                    posterPath = null,
                    backdropPath = null,
                    genres = "Drama",
                    rating = null,
                    year = null,
                    mediaType = type,
                    matchConfidence = 1f,
                    isUnidentified = false,
                    lastUpdated = 1,
                )
            // Room's ORDER BY title is binary: capitals first, so "Bleach" before "akira".
            val movies = listOf(item(1, "Bleach"), item(2, "akira"), item(3, "ébène"))
            val shows =
                listOf(item(4, "Zeta", MediaType.TV_SHOW), item(5, "alpha", MediaType.TV_SHOW))
            val recent =
                listOf(
                    item(2, "akira"),
                    item(4, "Zeta", MediaType.TV_SHOW),
                    item(1, "Bleach"),
                )
            val repo =
                FakeMediaRepository().apply {
                    byType = mapOf(MediaType.MOVIE to movies, MediaType.TV_SHOW to shows)
                    recentlyAdded = recent
                }
            val content =
                GetHomeContentUseCase(repo, EmptyHistory(), UnconfinedTestDispatcher(testScheduler))
                    .invoke()
                    .first()

            assertThat(content.movies.map { it.id }).isEqualTo(listOf(2L, 1L, 3L))
            assertThat(content.tvShows.map { it.id }).isEqualTo(listOf(5L, 4L))
            assertThat(content.genres.getValue("Drama").map { it.title })
                .isEqualTo(listOf("akira", "alpha", "Bleach", "Zeta", "ébène"))
            assertThat(content.recentlyAdded.map { it.id }).isEqualTo(listOf(2L, 4L, 1L))
        }

    @Test
    fun `genre shelves trim, drop blanks, list a title once per genre and sort by name`() {
        val base =
            MediaItem(
                id = 1,
                tmdbId = 1,
                title = "A",
                originalTitle = null,
                overview = null,
                posterPath = null,
                backdropPath = null,
                genres = " Drama , ,Drama,Ação ",
                rating = null,
                year = null,
                mediaType = MediaType.MOVIE,
                matchConfidence = 1f,
                isUnidentified = false,
                lastUpdated = 1,
            )
        val shelves =
            com.singam.lionlibrary.domain.usecase.genreShelves(
                listOf(base, base.copy(id = 2, genres = null), base.copy(id = 3, genres = "Comedy"))
            )

        assertThat(shelves.keys.toList()).isEqualTo(listOf("Ação", "Comedy", "Drama"))
        assertThat(shelves.getValue("Drama").map { it.id }).isEqualTo(listOf(1L))
    }

    @Test
    fun `only Japanese animation receives the Anime genre`() {
        val animated = MovieDetailsDto(1, "Film", genres = listOf(GenreDto(16, "Animation")))
        assertThat(animated.copy(originalLanguage = "ja").libraryGenres()).contains("Anime")
        assertThat(animated.copy(originCountry = listOf("JP")).libraryGenres()).contains("Anime")
        assertThat(
                animated.copy(originalLanguage = "en", originCountry = listOf("US")).libraryGenres()
            )
            .doesNotContain("Anime")
        assertThat(MovieDetailsDto(2, "Live action", originalLanguage = "ja").libraryGenres())
            .isEmpty()
    }
}

private class EmptyHistory : WatchProgressRepository {
    override fun getJumpBackInItems() = flowOf(emptyList<JumpBackInItem>())

    override fun getProgressForMedia(mediaId: Long) = flowOf(emptyList<WatchProgress>())

    override suspend fun getProgress(mediaId: Long, episodeId: Long): WatchProgress? = null

    override suspend fun markAsStarted(mediaId: Long, episodeId: Long) {}

    override suspend fun markAsWatched(mediaId: Long, episodeId: Long) {}

    override suspend fun markAsUnwatched(mediaId: Long, episodeId: Long) {}

    override suspend fun deleteProgress(mediaId: Long, episodeId: Long) {}

    override suspend fun deleteProgressForMedia(mediaId: Long) {}

    override suspend fun hideMediaFromJumpBackIn(mediaId: Long) {}

    override suspend fun clearAll() {}
}
