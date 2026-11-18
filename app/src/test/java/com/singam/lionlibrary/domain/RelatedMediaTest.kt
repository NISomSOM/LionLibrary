package com.singam.lionlibrary.domain

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.singam.lionlibrary.domain.model.MediaType
import com.singam.lionlibrary.domain.model.genreList
import com.singam.lionlibrary.domain.model.hasAnyGenre
import com.singam.lionlibrary.domain.usecase.relatedMedia
import com.singam.lionlibrary.testing.media
import org.junit.jupiter.api.Test

/**
 * Break vectors: the title listing itself, a genre that is a substring of another ("Action" vs
 * "Action & Adventure"), case and spacing differences in stored genres, a title without genres
 * (matching everything or nothing), the same id twice, ties in the ranking, and an empty selection.
 */
class RelatedMediaTest {
    @Test
    fun `the title itself and titles without a shared genre are left out`() {
        val target = media(1, genres = "Drama,Crime")
        val result =
            relatedMedia(
                target,
                listOf(target, media(2, genres = "Comedy"), media(3, genres = null), media(4)),
            )
        assertThat(result).isEmpty()
    }

    @Test
    fun `a genre that only contains another genre's name is not shared`() {
        val target = media(1, genres = "Action")
        val result = relatedMedia(target, listOf(media(2, genres = "Action & Adventure")))
        assertThat(result).isEmpty()
    }

    @Test
    fun `stored case and spacing do not hide a shared genre`() {
        val target = media(1, genres = "Science Fiction, Drama")
        val result = relatedMedia(target, listOf(media(2, genres = " science fiction ,Comedy")))
        assertThat(result.map { it.id }).containsExactly(2L)
    }

    @Test
    fun `more shared genres rank first, then the same type, then the title`() {
        val target = media(1, genres = "Drama,Crime,Mystery")
        val result =
            relatedMedia(
                target,
                listOf(
                    media(2, title = "Alpha", genres = "Drama", type = MediaType.TV_SHOW),
                    media(3, title = "Zulu", genres = "Drama"),
                    media(4, title = "Beta", genres = "Drama,Crime,Mystery"),
                    media(5, title = "alpha", genres = "Crime,Drama", type = MediaType.ANIME),
                ),
            )
        // 4 shares three, 5 shares two; 3 (same type as the movie) beats 2 despite its title.
        assertThat(result.map { it.id }).containsExactly(4L, 5L, 3L, 2L)
    }

    @Test
    fun `a title without genres has no related titles`() {
        val result = relatedMedia(media(1, genres = " , "), listOf(media(2, genres = "Drama")))
        assertThat(result).isEmpty()
    }

    @Test
    fun `the same title listed twice appears once`() {
        val target = media(1, genres = "Drama")
        val twice = media(2, genres = "Drama")
        assertThat(relatedMedia(target, listOf(twice, twice)).map { it.id }).containsExactly(2L)
    }

    @Test
    fun `genre list drops blanks and repeats`() {
        assertThat(media(1, genres = "Drama,, Drama ,😀 Anime").genreList)
            .containsExactly("Drama", "😀 Anime")
    }

    @Test
    fun `an empty selection matches titles without genres, a selection matches any of its genres`() {
        assertThat(media(1, genres = null).hasAnyGenre(emptySet())).isTrue()
        assertThat(media(1, genres = null).hasAnyGenre(setOf("Drama"))).isFalse()
        assertThat(media(2, genres = "Comedy,Drama").hasAnyGenre(setOf("horror", "DRAMA"))).isTrue()
    }
}
