package com.example.mediahub.data.repository

import com.example.mediahub.data.local.preferences.PreferencesManager
import com.example.mediahub.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow

class DataStoreSettingsRepository(
    private val preferencesManager: PreferencesManager
) : SettingsRepository {

    override val tmdbApiKey: Flow<String> = preferencesManager.tmdbApiKey
    override val moviesFolderUri: Flow<String> = preferencesManager.moviesFolderUri
    override val showsFolderUri: Flow<String> = preferencesManager.showsFolderUri
    override val animeFolderUri: Flow<String> = preferencesManager.animeFolderUri

    override val lastScanTime: Flow<Long> = preferencesManager.lastScanTime

    override suspend fun setTmdbApiKey(key: String) = preferencesManager.setTmdbApiKey(key)
    override suspend fun setMoviesFolderUri(uri: String) = preferencesManager.setMoviesFolderUri(uri)
    override suspend fun setShowsFolderUri(uri: String) = preferencesManager.setShowsFolderUri(uri)
    override suspend fun setAnimeFolderUri(uri: String) = preferencesManager.setAnimeFolderUri(uri)

    override suspend fun setLastScanTime(time: Long) = preferencesManager.setLastScanTime(time)
}
