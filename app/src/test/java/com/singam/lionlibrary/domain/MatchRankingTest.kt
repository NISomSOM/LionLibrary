package com.singam.lionlibrary.domain

import assertk.assertThat
import assertk.assertions.*
import com.singam.lionlibrary.domain.model.*
import com.singam.lionlibrary.util.ConfidenceScorer
import org.junit.jupiter.api.Test

class MatchRankingTest {
    @Test
    fun `best candidate wins even when returned last`() {
        assertThat(
                selectConfidentMatch(
                        listOf(MatchScore("wrong", .3f), MatchScore("right", .95f)),
                        .8f,
                    )
                    ?.candidate
            )
            .isEqualTo("right")
    }

    @Test
    fun `equally confident candidates remain local files`() {
        assertThat(selectConfidentMatch(listOf(MatchScore(1, .9f), MatchScore(2, .9f)), .8f))
            .isNull()
    }

    @Test
    fun `non finite scores and weak matches cannot identify a title`() {
        assertThat(
                selectConfidentMatch(
                    listOf(
                        MatchScore(1, Float.NaN),
                        MatchScore(2, Float.POSITIVE_INFINITY),
                        MatchScore(3, .2f),
                    ),
                    .8f,
                )
            )
            .isNull()
    }

    @Test
    fun `unicode and emoji titles can match without being stripped`() {
        assertThat(ConfidenceScorer.computeConfidence("千と千尋 😀", "千と千尋 😀", null, null))
            .isEqualTo(1f)
    }

    // Break vectors: same title/different year, truly ambiguous same-year releases,
    // weak text evidence even with a year, non-finite scores and result ordering.
    @Test
    fun `release year resolves capped exact title scores in either order`() {
        val current = MatchScore(936075, 1f, yearMatches = true)
        val old = MatchScore(55587, 1f)
        for (items in listOf(listOf(current, old), listOf(old, current))) {
            assertThat(selectConfidentMatch(items, .8f)?.candidate).isEqualTo(936075)
        }
        assertThat(selectConfidentMatch(listOf(current, MatchScore(42, 1f, true)), .8f)).isNull()
        assertThat(selectConfidentMatch(listOf(MatchScore(1, .79f, true)), .8f)).isNull()
    }
}
