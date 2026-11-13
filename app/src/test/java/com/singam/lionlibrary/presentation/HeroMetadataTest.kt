package com.singam.lionlibrary.presentation

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.singam.lionlibrary.presentation.home.firstHeroGenre
import com.singam.lionlibrary.presentation.home.heroFacts
import com.singam.lionlibrary.presentation.home.heroRuntimeParts
import org.junit.jupiter.api.Test

/**
 * Break vectors: blank/whitespace genre lists; Unicode genres; runtime boundaries (0, negative,
 * exactly one hour, very large); missing fields leaving gaps or separators; invalid years. Pure
 * functions only: no font, Android, clock or I/O dependencies.
 */
class HeroMetadataTest {
    @Test
    fun `facts follow type, first genre, runtime, year order`() {
        assertThat(heroFacts("Movie", "Drama, History", "2h 5m", 2025))
            .containsExactly("Movie", "Drama", "2h 5m", "2025")
    }

    @Test
    fun `missing pieces are skipped without empty entries`() {
        assertThat(heroFacts("Series", null, null, null)).containsExactly("Series")
        assertThat(heroFacts(" ", " , ", " ", 0)).isEqualTo(emptyList<String>())
    }

    @Test
    fun `first genre skips blank entries and keeps unicode whole`() {
        assertThat(firstHeroGenre(",  , 冒険👨‍👩‍👧‍👦, Drama")).isEqualTo("冒険👨‍👩‍👧‍👦")
        assertThat(firstHeroGenre(null)).isNull()
    }

    @Test
    fun `runtime splits into hours and minutes at boundaries`() {
        assertThat(heroRuntimeParts(59)).isEqualTo(0 to 59)
        assertThat(heroRuntimeParts(60)).isEqualTo(1 to 0)
        assertThat(heroRuntimeParts(125)).isEqualTo(2 to 5)
        assertThat(heroRuntimeParts(Int.MAX_VALUE))
            .isEqualTo(Int.MAX_VALUE / 60 to Int.MAX_VALUE % 60)
    }

    @Test
    fun `non positive or missing runtime has no parts`() {
        assertThat(heroRuntimeParts(0)).isNull()
        assertThat(heroRuntimeParts(-5)).isNull()
        assertThat(heroRuntimeParts(null)).isNull()
    }
}
