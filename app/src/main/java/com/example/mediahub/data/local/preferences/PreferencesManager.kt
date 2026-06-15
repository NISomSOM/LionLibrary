package com.example.mediahub.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.example.mediahub.util.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "lionlibrary_settings")

class PreferencesManager(context: Context) {

    private val dataStore = context.dataStore

    // --- Readers (Flow) ---

    val tmdbApiKey: Flow<String> = dataStore.data.map { prefs ->
        prefs[PreferencesKeys.TMDB_API_KEY] ?: ""
    }

    val moviesFolderUri: Flow<String> = dataStore.data.map { prefs ->
        prefs[PreferencesKeys.MOVIES_FOLDER_URI] ?: ""
    }

    val showsFolderUri: Flow<String> = dataStore.data.map { prefs ->
        prefs[PreferencesKeys.SHOWS_FOLDER_URI] ?: ""
    }

    val animeFolderUri: Flow<String> = dataStore.data.map { prefs ->
        prefs[PreferencesKeys.ANIME_FOLDER_URI] ?: ""
    }

    val lastScanTime: Flow<Long> = dataStore.data.map { prefs ->
        prefs[PreferencesKeys.LAST_SCAN_TIME] ?: 0L
    }

    val isSetupComplete: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[PreferencesKeys.IS_SETUP_COMPLETE] ?: false
    }

    // --- Writers (suspend) ---

    suspend fun setTmdbApiKey(key: String) {
        dataStore.edit { prefs -> prefs[PreferencesKeys.TMDB_API_KEY] = key }
    }

    suspend fun setMoviesFolderUri(uri: String) {
        dataStore.edit { prefs -> prefs[PreferencesKeys.MOVIES_FOLDER_URI] = uri }
    }

    suspend fun setShowsFolderUri(uri: String) {
        dataStore.edit { prefs -> prefs[PreferencesKeys.SHOWS_FOLDER_URI] = uri }
    }

    suspend fun setAnimeFolderUri(uri: String) {
        dataStore.edit { prefs -> prefs[PreferencesKeys.ANIME_FOLDER_URI] = uri }
    }

    suspend fun setLastScanTime(time: Long) {
        dataStore.edit { prefs -> prefs[PreferencesKeys.LAST_SCAN_TIME] = time }
    }

    suspend fun setSetupComplete(complete: Boolean) {
        dataStore.edit { prefs -> prefs[PreferencesKeys.IS_SETUP_COMPLETE] = complete }
    }
}
