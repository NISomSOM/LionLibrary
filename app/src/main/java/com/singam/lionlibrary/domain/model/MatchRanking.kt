package com.singam.lionlibrary.domain.model

data class MatchScore<T>(
    val candidate: T,
    val confidence: Float,
    val yearMatches: Boolean = false,
    val titleSimilarity: Float = confidence,
)

/** Ambiguous top scores require review rather than silently choosing an arbitrary result. */
fun <T> selectConfidentMatch(scores: List<MatchScore<T>>, threshold: Float): MatchScore<T>? {
    val ranked =
        scores
            .filter { it.confidence.isFinite() && it.titleSimilarity.isFinite() }
            .sortedWith(
                compareByDescending<MatchScore<T>> { it.confidence }
                    .thenByDescending { it.yearMatches }
                    .thenByDescending { it.titleSimilarity }
            )
    val best = ranked.firstOrNull() ?: return null
    if (best.confidence < threshold) return null
    val second = ranked.getOrNull(1)
    if (
        second?.confidence == best.confidence &&
            second.yearMatches == best.yearMatches &&
            second.titleSimilarity == best.titleSimilarity
    )
        return null
    return best
}
