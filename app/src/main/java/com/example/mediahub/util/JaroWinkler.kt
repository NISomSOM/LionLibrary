package com.example.mediahub.util

/**
 * Pure Kotlin implementation of Jaro-Winkler string similarity.
 * Returns a value between 0.0 (no similarity) and 1.0 (exact match).
 */
object JaroWinkler {

    private const val WINKLER_PREFIX_WEIGHT = 0.1
    private const val MAX_PREFIX_LENGTH = 4

    fun similarity(s1: String, s2: String): Float {
        if (s1 == s2) return 1.0f
        if (s1.isEmpty() || s2.isEmpty()) return 0.0f

        val jaroScore = jaroSimilarity(s1, s2)

        // Calculate common prefix (up to 4 chars)
        val prefixLength = s1.zip(s2)
            .takeWhile { (a, b) -> a == b }
            .count()
            .coerceAtMost(MAX_PREFIX_LENGTH)

        return (jaroScore + prefixLength * WINKLER_PREFIX_WEIGHT * (1 - jaroScore)).toFloat()
    }

    private fun jaroSimilarity(s1: String, s2: String): Double {
        val maxLen = maxOf(s1.length, s2.length)
        val matchDistance = (maxLen / 2) - 1

        val s1Matches = BooleanArray(s1.length)
        val s2Matches = BooleanArray(s2.length)

        var matches = 0
        var transpositions = 0

        // Find matches
        for (i in s1.indices) {
            val start = maxOf(0, i - matchDistance)
            val end = minOf(i + matchDistance + 1, s2.length)

            for (j in start until end) {
                if (s2Matches[j] || s1[i] != s2[j]) continue
                s1Matches[i] = true
                s2Matches[j] = true
                matches++
                break
            }
        }

        if (matches == 0) return 0.0

        // Count transpositions
        var k = 0
        for (i in s1.indices) {
            if (!s1Matches[i]) continue
            while (!s2Matches[k]) k++
            if (s1[i] != s2[k]) transpositions++
            k++
        }

        return (matches.toDouble() / s1.length +
                matches.toDouble() / s2.length +
                (matches - transpositions / 2.0) / matches) / 3.0
    }
}
