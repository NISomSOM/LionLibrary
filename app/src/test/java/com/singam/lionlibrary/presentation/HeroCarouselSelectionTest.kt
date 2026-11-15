package com.singam.lionlibrary.presentation

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import com.singam.lionlibrary.domain.model.MediaItem
import com.singam.lionlibrary.domain.model.MediaType
import com.singam.lionlibrary.presentation.home.selectHeroCarousel
import kotlin.random.Random
import org.junit.jupiter.api.Test

class HeroCarouselSelectionTest {
    // Break vectors: first scan emits a short library then grows (carousel pinned short), titles
    // removed between emissions, duplicate ids in input (anime films are in two shelves), refreshed
    // data for a kept title, library smaller than the carousel, zero/negative size.

    private fun item(id: Long, title: String = "Title $id") =
        MediaItem(
            id = id,
            tmdbId = id.toInt(),
            title = title,
            originalTitle = null,
            overview = null,
            posterPath = null,
            backdropPath = null,
            genres = null,
            rating = null,
            year = null,
            mediaType = MediaType.MOVIE,
            matchConfidence = 1f,
            isUnidentified = false,
            lastUpdated = 1,
        )

    @Test
    fun `short carousel from a first scan is topped up while keeping existing order`() {
        val first = selectHeroCarousel(emptyList(), listOf(item(1), item(2)), Random(1))
        val grown = (1L..30L).map { item(it) }

        val next = selectHeroCarousel(first, grown, Random(2))

        assertThat(next).hasSize(10)
        assertThat(next.take(2).map { it.id }).isEqualTo(first.map { it.id })
        assertThat(next.map { it.id }.toSet()).hasSize(10)
    }

    @Test
    fun `full carousel keeps the same ids and order when the library re-emits`() {
        val library = (1L..30L).map { item(it) }
        val first = selectHeroCarousel(emptyList(), library, Random(3))

        val again = selectHeroCarousel(first, library.shuffled(Random(4)), Random(5))

        assertThat(again.map { it.id }).isEqualTo(first.map { it.id })
    }

    @Test
    fun `removed title is replaced and the survivors keep their positions`() {
        val library = (1L..30L).map { item(it) }
        val first = selectHeroCarousel(emptyList(), library, Random(6))
        val removedId = first[3].id

        val next = selectHeroCarousel(first, library.filter { it.id != removedId }, Random(7))

        assertThat(next).hasSize(10)
        assertThat(next.take(9).map { it.id })
            .isEqualTo(first.filter { it.id != removedId }.map { it.id })
        assertThat(next.none { it.id == removedId }).isEqualTo(true)
    }

    @Test
    fun `duplicate ids in the input appear once`() {
        val duplicated = listOf(item(1), item(1), item(2), item(2), item(3))

        val result = selectHeroCarousel(listOf(item(1), item(1)), duplicated, Random(8))

        assertThat(result.map { it.id }.sorted()).containsExactly(1L, 2L, 3L)
        assertThat(result.first().id).isEqualTo(1L)
    }

    @Test
    fun `kept title uses the refreshed item data`() {
        val result =
            selectHeroCarousel(
                listOf(item(1, "Old name")),
                listOf(item(1, "見た目 New 🎬")),
                Random(9),
            )

        assertThat(result.single().title).isEqualTo("見た目 New 🎬")
    }

    @Test
    fun `non-positive size yields an empty carousel`() {
        assertThat(selectHeroCarousel(emptyList(), listOf(item(1)), Random(10), size = 0)).isEmpty()
        assertThat(selectHeroCarousel(listOf(item(1)), listOf(item(1)), Random(10), size = -1))
            .isEmpty()
    }
}
