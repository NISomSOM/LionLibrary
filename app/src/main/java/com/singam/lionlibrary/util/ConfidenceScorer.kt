package com.singam.lionlibrary.util

// Figure out how well a filename matches a TMDB result.
// Uses Jaro-Winkler with a year bonus.
object ConfidenceScorer {

    // Returns score between 0.0 and 1.0.
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

