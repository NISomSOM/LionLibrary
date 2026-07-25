package com.singam.lionlibrary.presentation.player.engine

import com.singam.lionlibrary.domain.model.MediaType

/**
 * Fuzzy language matcher for audio and subtitle track labels.
 *
 * Media files use wildly inconsistent track labelling — from clean ISO codes
 * (`eng`, `en`) to decorated strings (`[Standard] English -1`, `Eng - Surround 5.1`,
 * `JP Audio`). This utility normalises labels and matches them against known
 * patterns for English and Japanese.
 *
 * Used by [PlayerViewModel] to auto-select audio/subtitle tracks based on
 * [MediaType]:
 * - **MOVIE / TV_SHOW** → English audio, English subtitles
 * - **ANIME**           → Japanese audio, English subtitles
 */
object TrackLanguageMatcher {

    // -- Tokens that identify English -----------------------------------------
    // Word-boundary matching to avoid false positives (e.g. "jenglish")
    private val ENGLISH_TOKENS = setOf(
        "english", "eng", "en"
    )

    // -- Tokens that identify Japanese ----------------------------------------
    private val JAPANESE_TOKENS = setOf(
        "japanese", "jpn", "jp", "ja"
    )

    // -- CJK character range for 日本語 etc. -----------------------------------
    private val CJK_JAPANESE_REGEX = Regex("[\\u3040-\\u309F\\u30A0-\\u30FF\\u4E00-\\u9FFF]")

    // -- Tokens that indicate a signs/songs-only subtitle track ----------------
    private val SIGNS_TOKENS = setOf("signs", "songs", "s&s")

    /**
     * Returns `true` if [label] identifies an English-language track.
     *
     * Handles: `"English"`, `"eng"`, `"ENG"`, `"en"`,
     * `"[Standard] English -1"`, `"Eng - Surround 5.1"`, `"English (Stereo)"`.
     */
    fun isEnglish(label: String): Boolean = matchesLanguage(label, ENGLISH_TOKENS)

    /**
     * Returns `true` if [label] identifies a Japanese-language track.
     *
     * Handles: `"Japanese"`, `"jpn"`, `"JPN"`, `"ja"`, `"JP"`,
     * `"JP Audio"`, `"日本語"`.
     */
    fun isJapanese(label: String): Boolean {
        if (matchesLanguage(label, JAPANESE_TOKENS)) return true
        // Fallback: check for Japanese script characters (日本語 etc.)
        return CJK_JAPANESE_REGEX.containsMatchIn(label)
    }

    /**
     * Picks the best audio track for the given [mediaType].
     *
     * - **ANIME** → prefer Japanese, fall back to first track
     * - **MOVIE / TV_SHOW** → prefer English, fall back to first track
     *
     * Returns `null` if [tracks] is empty.
     */
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

    /**
     * Picks the best subtitle track — always prefers English.
     * Returns `null` if [tracks] is empty.
     */
    fun findBestSubtitleTrack(
        tracks: List<EngineTrackInfo>
    ): EngineTrackInfo? {
        if (tracks.isEmpty()) return null
        // Prefer English full-dialogue subs; deprioritize signs/songs-only tracks
        return tracks.firstOrNull { isEnglish(it.label) && !isSignsOnly(it.label) }
            ?: tracks.firstOrNull { isEnglish(it.label) }
            ?: tracks.firstOrNull { !isSignsOnly(it.label) }
            ?: tracks.first()
    }

    // -- Internal helpers -----------------------------------------------------

    /**
     * Tokenises [label] into alphanumeric words and checks whether any word
     * matches a token in [languageTokens].
     *
     * This prevents false positives from substring matching (e.g. "jenglish"
     * should NOT match "eng" or "english").
     */
    private fun matchesLanguage(label: String, languageTokens: Set<String>): Boolean {
        val words = label.lowercase().split(Regex("[^a-z0-9&]+")).filter { it.isNotEmpty() }
        return words.any { it in languageTokens }
    }

    /**
     * Returns `true` if [label] indicates a signs/songs-only subtitle track
     * (e.g. `"[signs]"`, `"Signs & Songs"`, `"English (Signs/Songs)"`).
     * These tracks only translate on-screen text, not dialogue.
     */
    private fun isSignsOnly(label: String): Boolean {
        val words = label.lowercase().split(Regex("[^a-z0-9&]+")).filter { it.isNotEmpty() }
        return words.any { it in SIGNS_TOKENS }
    }
}
