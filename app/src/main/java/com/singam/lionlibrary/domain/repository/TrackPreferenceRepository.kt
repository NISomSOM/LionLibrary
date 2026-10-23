package com.singam.lionlibrary.domain.repository

import com.singam.lionlibrary.domain.model.TrackPreference

/** Per-title audio and subtitle choices, keyed by the movie or show id. */
interface TrackPreferenceRepository {
    suspend fun get(mediaId: Long): TrackPreference?

    suspend fun save(mediaId: Long, preference: TrackPreference)
}
