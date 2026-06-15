package com.example.mediahub.util

/**
 * Computes match confidence between a parsed filename and a TMDB result.
 * Uses Jaro-Winkler similarity on titles with a year bonus.
 */
object ConfidenceScorer {

    /**
     * @return confidence score between 0.0 and 1.0
     */
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
