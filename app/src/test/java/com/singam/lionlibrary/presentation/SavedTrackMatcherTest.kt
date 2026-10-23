package com.singam.lionlibrary.presentation

import assertk.assertThat
import assertk.assertions.*
import com.singam.lionlibrary.domain.model.SavedTrack
import com.singam.lionlibrary.presentation.player.engine.EngineTrackInfo
import com.singam.lionlibrary.presentation.player.engine.SavedTrackMatcher
import org.junit.jupiter.api.Test

// Break vectors: two-letter vs three-letter codes across engines, case and whitespace in labels,
// several tracks sharing a language (full vs signs), sidecar vs built-in with equal labels,
// missing/"und" languages, a remembered track absent from the new file, and an empty list.
class SavedTrackMatcherTest {
    private fun track(
        id: String,
        label: String,
        language: String? = null,
        isExternal: Boolean = false,
    ) = EngineTrackInfo(id, label, isSelected = false, language = language, isExternal = isExternal)

    @Test
    fun `exact language and label wins over a same-language sibling`() {
        val tracks =
            listOf(
                track("1", "English Signs & Songs", "en"),
                track("2", "English Full", "en"),
            )
        val saved = SavedTrackMatcher.toSaved(track("x", "English Full", "en"))
        assertThat(SavedTrackMatcher.find(tracks, saved)?.id).isEqualTo("2")
    }

    @Test
    fun `ExoPlayer two-letter code matches a libVLC three-letter code`() {
        val saved = SavedTrackMatcher.toSaved(track("x", "Japanese", "ja"))
        val vlcTracks = listOf(track("10", "English", "eng"), track("11", "Track 2", "jpn"))
        assertThat(SavedTrackMatcher.find(vlcTracks, saved)?.id).isEqualTo("11")
    }

    @Test
    fun `label matching ignores case and surrounding whitespace`() {
        val saved = SavedTrack(language = null, label = "  commentary ")
        val tracks = listOf(track("1", "Main"), track("2", "Commentary"))
        assertThat(SavedTrackMatcher.find(tracks, saved)?.id).isEqualTo("2")
    }

    @Test
    fun `sidecar choice prefers the sidecar when a built-in track has the same label`() {
        val saved = SavedTrackMatcher.toSaved(track("x", "English", "en", isExternal = true))
        val tracks =
            listOf(track("1", "English", "en"), track("2", "English", "en", isExternal = true))
        assertThat(SavedTrackMatcher.find(tracks, saved)?.id).isEqualTo("2")
    }

    @Test
    fun `undetermined language never matches by language alone`() {
        val saved = SavedTrack(language = "und", label = "Something else")
        val tracks = listOf(track("1", "Track 1", "und"), track("2", "Track 2", null))
        assertThat(SavedTrackMatcher.find(tracks, saved)).isNull()
    }

    @Test
    fun `nothing matches when the remembered language is missing from the file`() {
        val saved = SavedTrackMatcher.toSaved(track("x", "French", "fr"))
        assertThat(SavedTrackMatcher.find(listOf(track("1", "English", "en")), saved)).isNull()
        assertThat(SavedTrackMatcher.find(emptyList(), saved)).isNull()
    }

    @Test
    fun `unknown language codes are kept as written`() {
        val saved = SavedTrackMatcher.toSaved(track("x", "Klingon", "tlh"))
        assertThat(saved.language).isEqualTo("tlh")
        assertThat(SavedTrackMatcher.find(listOf(track("1", "Other", "TLH")), saved)?.id)
            .isEqualTo("1")
    }
}
