package com.singam.lionlibrary.data.repository

import com.singam.lionlibrary.data.local.preferences.PreferencesManager
import com.singam.lionlibrary.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow

class DataStoreSettingsRepository(private val preferencesManager: PreferencesManager) :
    SettingsRepository {

    override val keyVerified = preferencesManager.keyVerified
    override val setupDismissed = preferencesManager.setupDismissed
    override val scanSummary = preferencesManager.scanSummary

    override suspend fun setKeyVerified(verified: Boolean) =
        preferencesManager.setKeyVerified(verified)

    override suspend fun setSetupDismissed(dismissed: Boolean) =
        preferencesManager.setSetupDismissed(dismissed)

    override suspend fun setScanSummary(summary: com.singam.lionlibrary.domain.model.ScanSummary) =
        preferencesManager.setScanSummary(summary)

    override val tmdbApiKey: Flow<String> = preferencesManager.tmdbApiKey
    override val moviesFolderUri: Flow<String> = preferencesManager.moviesFolderUri
    override val showsFolderUri: Flow<String> = preferencesManager.showsFolderUri

    override val lastScanTime: Flow<Long> = preferencesManager.lastScanTime

    override suspend fun setTmdbApiKey(key: String) = preferencesManager.setTmdbApiKey(key)

    override suspend fun setMoviesFolderUri(uri: String) =
        preferencesManager.setMoviesFolderUri(uri)

    override suspend fun setShowsFolderUri(uri: String) = preferencesManager.setShowsFolderUri(uri)

    override suspend fun setLastScanTime(time: Long) = preferencesManager.setLastScanTime(time)

    override val forceLibVlc: Flow<Boolean> = preferencesManager.forceLibVlc

    override suspend fun setForceLibVlc(enabled: Boolean) =
        preferencesManager.setForceLibVlc(enabled)

    override val forceExternalPlayer: Flow<Boolean> = preferencesManager.forceExternalPlayer

    override suspend fun setForceExternalPlayer(enabled: Boolean) =
        preferencesManager.setForceExternalPlayer(enabled)

    override val onlineSkipTimes: Flow<Boolean> = preferencesManager.onlineSkipTimes

    override suspend fun setOnlineSkipTimes(enabled: Boolean) =
        preferencesManager.setOnlineSkipTimes(enabled)
}
