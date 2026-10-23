package com.singam.lionlibrary.presentation

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.singam.lionlibrary.presentation.home.fitHeroMetadata
import org.junit.jupiter.api.Test

/**
 * Break vectors: exact width boundary; one oversized genre; even the first genre too wide;
 * blank/missing fields; Unicode/emoji truncation. Width is a supplied pure predicate: no font,
 * Android, clock or I/O dependencies. Numeric/state/concurrency vectors do not apply.
 */
class HeroMetadataTest {
    @Test
    fun `exact fit keeps both genres`() {
        val full = "2023  ·  Animation · Drama  ·  ★ 8.1"
        assertThat(
                fitHeroMetadata("2023", "Animation, Drama", "★ 8.1") { it.length <= full.length }
            )
            .isEqualTo(full)
    }

    @Test
    fun `oversized second genre is removed first`() {
        assertThat(fitHeroMetadata("2023", "Drama, Science Fiction", "★ 8.1") { it.length <= 25 })
            .isEqualTo("2023  ·  Drama  ·  ★ 8.1")
    }

    @Test
    fun `oversized first genre yields year and rating`() {
        assertThat(fitHeroMetadata("2023", "Very long genre, Drama", "★ 8.1") { it.length <= 18 })
            .isEqualTo("2023  ·  ★ 8.1")
    }

    @Test
    fun `blank fields do not consume genre slots or leave separators`() {
        assertThat(fitHeroMetadata(null, ",  , Drama, Animation", " ") { true })
            .isEqualTo("Drama · Animation")
        assertThat(fitHeroMetadata(null, null, null) { true }).isEqualTo("")
    }

    @Test
    fun `unicode genres are retained whole or omitted whole`() {
        val genre = "冒険👨‍👩‍👧‍👦"
        assertThat(fitHeroMetadata("2023", "$genre, ドラマ", null) { it == "2023  ·  $genre" })
            .isEqualTo("2023  ·  $genre")
    }

    @Test
    fun `tiny width keeps essential metadata for single line ellipsis`() {
        assertThat(fitHeroMetadata("2023", "Drama", "★ 8.1") { false }).isEqualTo("2023  ·  ★ 8.1")
    }
}
