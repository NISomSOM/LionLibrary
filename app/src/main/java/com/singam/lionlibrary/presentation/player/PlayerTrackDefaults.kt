package com.singam.lionlibrary.presentation.player

import com.singam.lionlibrary.domain.model.MediaType
import com.singam.lionlibrary.domain.model.TrackPreference
import com.singam.lionlibrary.presentation.player.engine.EngineTrackInfo
import com.singam.lionlibrary.presentation.player.engine.LionPlayerEngine
import com.singam.lionlibrary.presentation.player.engine.SavedTrackMatcher
import com.singam.lionlibrary.presentation.player.engine.TrackLanguageMatcher

/**
 * Default audio/subtitle choice for one file, applied once each as tracks arrive: the title's
 * remembered choice first, then the language rules (English, or Japanese audio for anime). A manual
 * choice or "Subtitles off" marks that side as decided, so later track discovery never overrides
 * it. [reset] starts a new file; nothing here touches the engine except through [apply].
 */
internal class PlayerTrackDefaults {
    /** Remembered choice for the current title; null until loaded or when there is none. */
    var preference: TrackPreference? = null
        private set

    private var audioApplied = false
    private var subtitleApplied = false

    fun reset(preference: TrackPreference?) {
        this.preference = preference
        audioApplied = false
        subtitleApplied = false
    }

    fun apply(engine: LionPlayerEngine, languageType: MediaType) {
        val saved = preference
        val audioTracks = engine.getAudioTracks()
        if (!audioApplied)
            saved
                ?.audio
                ?.let { SavedTrackMatcher.find(audioTracks, it) }
                ?.let { select(engine, it, audio = true) }
        val subtitleTracks = engine.getSubtitleTracks()
        if (!subtitleApplied && saved?.subtitlesOff == true && subtitleTracks.isNotEmpty()) {
            engine.selectSubtitleTrack(null)
            subtitleApplied = true
        }
        if (!subtitleApplied)
            saved
                ?.subtitle
                ?.let { SavedTrackMatcher.find(subtitleTracks, it) }
                ?.let {
                    select(engine, it, audio = false)
                }
        // Without a remembered match, fall back to the language defaults.
        if (!audioApplied)
            TrackLanguageMatcher.findBestAudioTrack(engine.getAudioTracks(), languageType)?.let {
                select(engine, it, audio = true)
            }
        if (!subtitleApplied)
            TrackLanguageMatcher.findBestSubtitleTrack(engine.getSubtitleTracks())?.let {
                select(engine, it, audio = false)
            }
    }

    /** A viewer's audio pick: decided for this file and remembered for the title. */
    fun audioChosen(chosen: EngineTrackInfo?): TrackPreference? {
        audioApplied = true
        return chosen?.let { update { copy(audio = SavedTrackMatcher.toSaved(it)) } }
    }

    /** A viewer's subtitle pick (null id = off): decided and remembered. */
    fun subtitleChosen(id: String?, chosen: EngineTrackInfo?): TrackPreference? {
        subtitleApplied = true
        return when {
            id == null -> update { copy(subtitlesOff = true) }
            chosen != null ->
                update { copy(subtitle = SavedTrackMatcher.toSaved(chosen), subtitlesOff = false) }
            else -> null
        }
    }

    private fun update(change: TrackPreference.() -> TrackPreference): TrackPreference =
        (preference ?: TrackPreference()).change().also { preference = it }

    private fun select(engine: LionPlayerEngine, track: EngineTrackInfo, audio: Boolean) {
        if (audio) {
            engine.selectAudioTrack(track.id)
            audioApplied = true
        } else {
            engine.selectSubtitleTrack(track.id)
            subtitleApplied = true
        }
    }
}
