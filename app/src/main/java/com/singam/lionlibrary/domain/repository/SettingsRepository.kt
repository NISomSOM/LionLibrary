package com.singam.lionlibrary.domain.repository

import com.singam.lionlibrary.domain.model.ScanSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface SettingsRepository {
    val keyVerified: Flow<Boolean>
    val setupDismissed: Flow<Boolean>
    val scanSummary: Flow<ScanSummary>

    suspend fun setKeyVerified(verified: Boolean)

    suspend fun setSetupDismissed(dismissed: Boolean)

    suspend fun setScanSummary(summary: ScanSummary)

    val tmdbApiKey: Flow<String>
    val moviesFolderUri: Flow<String>
    val showsFolderUri: Flow<String>

    val lastScanTime: Flow<Long>

    suspend fun setTmdbApiKey(key: String)

    suspend fun setMoviesFolderUri(uri: String)

    suspend fun setShowsFolderUri(uri: String)

    suspend fun setLastScanTime(time: Long)

    val forceLibVlc: Flow<Boolean>

    suspend fun setForceLibVlc(enabled: Boolean)

    val forceExternalPlayer: Flow<Boolean>

    suspend fun setForceExternalPlayer(enabled: Boolean)

    /** Whether the player may look up intro/credits times online (TheIntroDB/SkipDB). */
    val onlineSkipTimes: Flow<Boolean>

    suspend fun setOnlineSkipTimes(enabled: Boolean)

    /**
     * Whether a tapped poster flies into the Details page (on) or the page simply fades in with a
     * banner hero (off). Defaults keep test doubles that do not care working.
     */
    val posterTransitions: Flow<Boolean>
        get() = flowOf(true)

    suspend fun setPosterTransitions(enabled: Boolean) {}

    /**
     * Whether the Details page sits on a blurred copy of the title's artwork (on) or on solid black
     * (off).
     */
    val detailsBlur: Flow<Boolean>
        get() = flowOf(true)

    suspend fun setDetailsBlur(enabled: Boolean) {}

    /**
     * Whether the player draws chapter marks and the chapter name on the seek bar. Display only:
     * chapter-based skip buttons work either way.
     */
    val showChapters: Flow<Boolean>
        get() = flowOf(true)

    suspend fun setShowChapters(enabled: Boolean) {}
}
