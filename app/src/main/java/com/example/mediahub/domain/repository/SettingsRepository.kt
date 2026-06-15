package com.example.mediahub.domain.repository

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {

    val tmdbApiKey: Flow<String>
    val moviesFolderUri: Flow<String>
    val showsFolderUri: Flow<String>
    val animeFolderUri: Flow<String>

    val lastScanTime: Flow<Long>
    val isSetupComplete: Flow<Boolean>

    suspend fun setTmdbApiKey(key: String)
    suspend fun setMoviesFolderUri(uri: String)
    suspend fun setShowsFolderUri(uri: String)
    suspend fun setAnimeFolderUri(uri: String)

    suspend fun setLastScanTime(time: Long)
    suspend fun setSetupComplete(complete: Boolean)
}
