package com.singam.lionlibrary.presentation.player.engine

import com.singam.lionlibrary.domain.model.SavedTrack
import java.util.Locale
import java.util.MissingResourceException

/**
 * Finds the track in a newly opened file that best matches a remembered choice. Ids are not stable
 * across episodes or engines, so matching uses language and label, strongest first.
 */
object SavedTrackMatcher {
    fun find(tracks: List<EngineTrackInfo>, saved: SavedTrack): EngineTrackInfo? {
        if (tracks.isEmpty()) return null
        val language = saved.language.normalized()
        val label = saved.label.trim().lowercase()
        fun EngineTrackInfo.sameLanguage() =
            language != null && this.language.normalized() == language
        fun EngineTrackInfo.sameLabel() = this.label.trim().lowercase() == label
        val sameSource = tracks.filter { it.isExternal == saved.isExternal }
        return sameSource.firstOrNull { it.sameLanguage() && it.sameLabel() }
            ?: tracks.firstOrNull { it.sameLanguage() && it.sameLabel() }
            ?: sameSource.firstOrNull { it.sameLabel() }
            ?: sameSource.firstOrNull { it.sameLanguage() }
            ?: tracks.firstOrNull { it.sameLabel() }
            ?: tracks.firstOrNull { it.sameLanguage() }
    }

    fun toSaved(track: EngineTrackInfo) =
        SavedTrack(
            language = track.language.normalized(),
            label = track.label,
            isExternal = track.isExternal,
        )

    // ExoPlayer reports "en" where libVLC may report "eng"; compare three-letter codes.
    private fun String?.normalized(): String? {
        val code = this?.trim()?.lowercase()?.takeIf { it.isNotEmpty() && it != "und" }
        return code?.let {
            try {
                Locale.forLanguageTag(it).isO3Language.ifEmpty { it }
            } catch (_: MissingResourceException) {
                it
            }
        }
    }
}
