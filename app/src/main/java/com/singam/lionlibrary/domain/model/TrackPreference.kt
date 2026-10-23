package com.singam.lionlibrary.domain.model

import kotlinx.serialization.Serializable

/**
 * Audio and subtitle choice remembered for one movie or show. Tracks are matched again by language
 * and label in each file, because track ids differ between episodes and engines.
 */
@Serializable
data class TrackPreference(
    val audio: SavedTrack? = null,
    val subtitle: SavedTrack? = null,
    /** True when the viewer turned subtitles off; wins over [subtitle]. */
    val subtitlesOff: Boolean = false,
)

@Serializable
data class SavedTrack(
    val language: String? = null,
    val label: String,
    val isExternal: Boolean = false,
)
