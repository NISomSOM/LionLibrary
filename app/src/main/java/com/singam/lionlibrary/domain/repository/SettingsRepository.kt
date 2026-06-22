package com.singam.lionlibrary.domain.repository

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {

    val tmdbApiKey: Flow<String>
    val moviesFolderUri: Flow<String>
    val showsFolderUri: Flow<String>
    val animeFolderUri: Flow<String>

    val lastScanTime: Flow<Long>

    suspend fun setTmdbApiKey(key: String)
    suspend fun setMoviesFolderUri(uri: String)
    suspend fun setShowsFolderUri(uri: String)
    suspend fun setAnimeFolderUri(uri: String)

    suspend fun setLastScanTime(time: Long)
}

