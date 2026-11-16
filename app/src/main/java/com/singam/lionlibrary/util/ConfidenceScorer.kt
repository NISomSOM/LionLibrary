package com.singam.lionlibrary.util

import java.util.Locale

// Match scoring.
// Jaro-Winkler with year bonus.
object ConfidenceScorer {

    fun computeConfidence(
        parsedTitle: String,
        tmdbTitle: String,
        parsedYear: Int?,
        tmdbYear: Int?,
    ): Float {
        val titleSimilarity =
            JaroWinkler.similarity(
                parsedTitle.lowercase(Locale.ROOT).trim(),
                tmdbTitle.lowercase(Locale.ROOT).trim(),
            )
        val yearBonus = if (parsedYear != null && parsedYear == tmdbYear) 0.1f else 0f
        return (titleSimilarity + yearBonus).coerceAtMost(1.0f)
    }
}
