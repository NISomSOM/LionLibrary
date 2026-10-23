package com.singam.lionlibrary.domain.repository

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val keyVerified: Flow<Boolean>
    val setupDismissed: Flow<Boolean>
    val scanSummary: Flow<com.singam.lionlibrary.domain.model.ScanSummary>

    suspend fun setKeyVerified(verified: Boolean)

    suspend fun setSetupDismissed(dismissed: Boolean)

    suspend fun setScanSummary(summary: com.singam.lionlibrary.domain.model.ScanSummary)

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
}
