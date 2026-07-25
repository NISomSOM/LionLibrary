package com.singam.lionlibrary.presentation.player.engine

import com.singam.lionlibrary.domain.model.MediaType

/**
 * Match and normalize inconsistent audio and subtitle track labels.
 */
object TrackLanguageMatcher {

    // Identifiers for English language tracks
    // Word-boundary check prevents matching partial words like "jenglish"
    private val ENGLISH_TOKENS = setOf(
        "english", "eng", "en"
    )

    // Identifiers for Japanese language tracks
    private val JAPANESE_TOKENS = setOf(
        "japanese", "jpn", "jp", "ja"
    )

    // Matches CJK characters (e.g., 日本語)
    private val CJK_JAPANESE_REGEX = Regex("[\\u3040-\\u309F\\u30A0-\\u30FF\\u4E00-\\u9FFF]")

    // Identifiers for 'signs and songs' only subtitle tracks
    private val SIGNS_TOKENS = setOf("signs", "songs", "s&s")

    /** Check if track label indicates English. */
    fun isEnglish(label: String): Boolean = matchesLanguage(label, ENGLISH_TOKENS)

    /** Check if track label indicates Japanese. */
    fun isJapanese(label: String): Boolean {
        if (matchesLanguage(label, JAPANESE_TOKENS)) return true
        // Also check if string contains Japanese characters
        return CJK_JAPANESE_REGEX.containsMatchIn(label)
    }

    /** Select the most appropriate audio track for the media type. */
    fun findBestAudioTrack(
        tracks: List<EngineTrackInfo>,
        mediaType: MediaType
    ): EngineTrackInfo? {
        if (tracks.isEmpty()) return null

        val preferred: (String) -> Boolean = when (mediaType) {
            MediaType.ANIME -> ::isJapanese
            else -> ::isEnglish
        }

        return tracks.firstOrNull { preferred(it.label) } ?: tracks.first()
    }

    /** Select the best English subtitle track. */
    fun findBestSubtitleTrack(
        tracks: List<EngineTrackInfo>
    ): EngineTrackInfo? {
        if (tracks.isEmpty()) return null
        // Prefer full English subtitles, drop priority for signs-only tracks
        return tracks.firstOrNull { isEnglish(it.label) && !isSignsOnly(it.label) }
            ?: tracks.firstOrNull { isEnglish(it.label) }
            ?: tracks.firstOrNull { !isSignsOnly(it.label) }
            ?: tracks.first()
    }

    // Internal helper functions

    /** Check if label words match any of the provided language tokens. */
    private fun matchesLanguage(label: String, languageTokens: Set<String>): Boolean {
        val words = label.lowercase().split(Regex("[^a-z0-9&]+")).filter { it.isNotEmpty() }
        return words.any { it in languageTokens }
    }

    /** Check if subtitle track is meant only for signs and songs. */
    private fun isSignsOnly(label: String): Boolean {
        val words = label.lowercase().split(Regex("[^a-z0-9&]+")).filter { it.isNotEmpty() }
        return words.any { it in SIGNS_TOKENS }
    }
}
