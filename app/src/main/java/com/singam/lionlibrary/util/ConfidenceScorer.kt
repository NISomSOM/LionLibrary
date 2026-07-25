package com.singam.lionlibrary.util

// Scores filename matches against TMDB results.
// Uses Jaro-Winkler, adds a bonus if years match.
object ConfidenceScorer {

    // Returns a score from 0.0 to 1.0.
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

