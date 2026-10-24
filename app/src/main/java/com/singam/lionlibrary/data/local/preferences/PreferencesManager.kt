package com.singam.lionlibrary.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by
    preferencesDataStore(name = "lionlibrary_settings")

private val portableMigrated = booleanPreferencesKey("portable_preferences_migrated")
private val Context.playbackDataStore: DataStore<Preferences> by
    preferencesDataStore(
        name = "lionlibrary_playback",
        produceMigrations = { context ->
            listOf(
                object : DataMigration<Preferences> {
                    override suspend fun shouldMigrate(currentData: Preferences) =
                        currentData[portableMigrated] != true

                    override suspend fun migrate(currentData: Preferences): Preferences {
                        val legacy = context.dataStore.data.first()
                        return currentData.toMutablePreferences().apply {
                            if (this[PreferencesKeys.FORCE_LIBVLC] == null)
                                this[PreferencesKeys.FORCE_LIBVLC] =
                                    legacy[PreferencesKeys.FORCE_LIBVLC] ?: false
                            if (this[PreferencesKeys.FORCE_EXTERNAL_PLAYER] == null)
                                this[PreferencesKeys.FORCE_EXTERNAL_PLAYER] =
                                    legacy[PreferencesKeys.FORCE_EXTERNAL_PLAYER] ?: false
                            this[portableMigrated] = true
                        }
                    }

                    override suspend fun cleanUp() {}
                }
            )
        },
    )

class PreferencesManager(context: Context) {
    private val portable = context.playbackDataStore

    private val dataStore = context.dataStore

    val keyVerified: Flow<Boolean> =
        dataStore.data.map { it[PreferencesKeys.KEY_VERIFIED] ?: false }
    val setupDismissed: Flow<Boolean> =
        dataStore.data.map { it[PreferencesKeys.SETUP_DISMISSED] ?: false }
    val scanSummary: Flow<com.singam.lionlibrary.domain.model.ScanSummary> =
        dataStore.data.map {
            val encoded = it[PreferencesKeys.SCAN_SUMMARY]
            if (encoded == null) com.singam.lionlibrary.domain.model.ScanSummary()
            else
                try {
                    kotlinx.serialization.json.Json.decodeFromString<
                        com.singam.lionlibrary.domain.model.ScanSummary
                    >(
                        encoded
                    )
                } catch (_: kotlinx.serialization.SerializationException) {
                    com.singam.lionlibrary.domain.model.ScanSummary()
                }
        }

    suspend fun setKeyVerified(value: Boolean) {
        dataStore.edit { it[PreferencesKeys.KEY_VERIFIED] = value }
    }

    suspend fun setSetupDismissed(value: Boolean) {
        dataStore.edit { it[PreferencesKeys.SETUP_DISMISSED] = value }
    }

    suspend fun setScanSummary(value: com.singam.lionlibrary.domain.model.ScanSummary) {
        dataStore.edit {
            it[PreferencesKeys.SCAN_SUMMARY] =
                kotlinx.serialization.json.Json.encodeToString(
                    com.singam.lionlibrary.domain.model.ScanSummary.serializer(),
                    value,
                )
        }
    }

    val tmdbApiKey: Flow<String> =
        dataStore.data.map { prefs -> prefs[PreferencesKeys.TMDB_API_KEY] ?: "" }

    val moviesFolderUri: Flow<String> =
        dataStore.data.map { prefs -> prefs[PreferencesKeys.MOVIES_FOLDER_URI] ?: "" }

    val showsFolderUri: Flow<String> =
        dataStore.data.map { prefs -> prefs[PreferencesKeys.SHOWS_FOLDER_URI] ?: "" }

    val lastScanTime: Flow<Long> =
        dataStore.data.map { prefs -> prefs[PreferencesKeys.LAST_SCAN_TIME] ?: 0L }

    suspend fun setTmdbApiKey(key: String) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.TMDB_API_KEY] = key
            prefs[PreferencesKeys.KEY_VERIFIED] = false
        }
    }

    suspend fun setMoviesFolderUri(uri: String) {
        dataStore.edit { prefs -> prefs[PreferencesKeys.MOVIES_FOLDER_URI] = uri }
    }

    suspend fun setShowsFolderUri(uri: String) {
        dataStore.edit { prefs -> prefs[PreferencesKeys.SHOWS_FOLDER_URI] = uri }
    }

    suspend fun setLastScanTime(time: Long) {
        dataStore.edit { prefs -> prefs[PreferencesKeys.LAST_SCAN_TIME] = time }
    }

    val forceLibVlc: Flow<Boolean> =
        portable.data.map { prefs -> prefs[PreferencesKeys.FORCE_LIBVLC] ?: false }

    suspend fun setForceLibVlc(enabled: Boolean) {
        portable.edit { prefs -> prefs[PreferencesKeys.FORCE_LIBVLC] = enabled }
    }

    val forceExternalPlayer: Flow<Boolean> =
        portable.data.map { prefs -> prefs[PreferencesKeys.FORCE_EXTERNAL_PLAYER] ?: false }

    suspend fun setForceExternalPlayer(enabled: Boolean) {
        portable.edit { prefs -> prefs[PreferencesKeys.FORCE_EXTERNAL_PLAYER] = enabled }
    }

    /** Online intro/credits lookups; on by default. File chapters work regardless. */
    val onlineSkipTimes: Flow<Boolean> =
        portable.data.map { prefs -> prefs[PreferencesKeys.ONLINE_SKIP_TIMES] ?: true }

    suspend fun setOnlineSkipTimes(enabled: Boolean) {
        portable.edit { prefs -> prefs[PreferencesKeys.ONLINE_SKIP_TIMES] = enabled }
    }
}
