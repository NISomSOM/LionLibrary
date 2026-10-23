package com.singam.lionlibrary.presentation

import assertk.assertThat
import assertk.assertions.*
import com.singam.lionlibrary.data.mapper.libraryGenres
import com.singam.lionlibrary.data.remote.dto.*
import com.singam.lionlibrary.domain.model.*
import com.singam.lionlibrary.domain.repository.WatchProgressRepository
import com.singam.lionlibrary.domain.usecase.GetHomeContentUseCase
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
        val series = film.copy(id = 8, mediaType = MediaType.ANIME, filePath = null)
        val repo =
            FakeMediaRepository().apply {
                byType = mapOf(MediaType.MOVIE to listOf(film), MediaType.ANIME to listOf(series))
            }
        val content =
            GetHomeContentUseCase(repo, EmptyHistory(), UnconfinedTestDispatcher(testScheduler))
                .invoke()
                .first()
        assertThat(content.movies.map { it.id }).isEqualTo(listOf(7L))
        assertThat(content.anime.map { it.id }).isEqualTo(listOf(8L, 7L))
        assertThat(content.anime.last().mediaType).isEqualTo(MediaType.MOVIE)
        assertThat(content.anime.last().filePath).isEqualTo(film.filePath)
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
