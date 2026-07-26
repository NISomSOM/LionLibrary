package com.singam.lionlibrary.util

// Match scoring.
// Jaro-Winkler with year bonus.
object ConfidenceScorer {

    fun computeConfidence(
        parsedTitle: String,
        tmdbTitle: String,
        parsedYear: Int?,
        tmdbYear: Int?
    ): Float {
        val titleSimilarity = JaroWinkler.similarity(
            parsedTitle.lowercase().trim(),
            tmdbTitle.lowercase().trim()
        )
        val yearBonus = if (parsedYear != null && parsedYear == tmdbYear) 0.1f else 0f
        return (titleSimilarity + yearBonus).coerceAtMost(1.0f)
    }
}
