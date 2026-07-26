package com.singam.lionlibrary.presentation.player.engine

import com.singam.lionlibrary.domain.model.MediaType

// Match audio/subtitle labels.
object TrackLanguageMatcher {

    private val ENGLISH_TOKENS = setOf(
        "english", "eng", "en"
    )

    private val JAPANESE_TOKENS = setOf(
        "japanese", "jpn", "jp", "ja"
    )

    // Match Japanese characters.
    private val CJK_JAPANESE_REGEX = Regex("[\\u3040-\\u309F\\u30A0-\\u30FF\\u4E00-\\u9FFF]")

    // Signs and songs identifiers.
    private val SIGNS_TOKENS = setOf("signs", "songs", "s&s")

    // Is English?
    fun isEnglish(label: String): Boolean = matchesLanguage(label, ENGLISH_TOKENS)

    // Is Japanese?
    fun isJapanese(label: String): Boolean {
        if (matchesLanguage(label, JAPANESE_TOKENS)) return true
        return CJK_JAPANESE_REGEX.containsMatchIn(label)
    }

    // Best audio track.
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

    // Best subtitle track.
    fun findBestSubtitleTrack(
        tracks: List<EngineTrackInfo>
    ): EngineTrackInfo? {
        if (tracks.isEmpty()) return null
        return tracks.firstOrNull { isEnglish(it.label) && !isSignsOnly(it.label) }
            ?: tracks.firstOrNull { isEnglish(it.label) }
            ?: tracks.firstOrNull { !isSignsOnly(it.label) }
            ?: tracks.first()
    }

    // Language match check.
    private fun matchesLanguage(label: String, languageTokens: Set<String>): Boolean {
        val words = label.lowercase().split(Regex("[^a-z0-9&]+")).filter { it.isNotEmpty() }
        return words.any { it in languageTokens }
    }

    // Signs and songs check.
    private fun isSignsOnly(label: String): Boolean {
        val words = label.lowercase().split(Regex("[^a-z0-9&]+")).filter { it.isNotEmpty() }
        return words.any { it in SIGNS_TOKENS }
    }
}
