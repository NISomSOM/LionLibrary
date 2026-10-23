package com.singam.lionlibrary.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.singam.lionlibrary.domain.model.TrackPreference
import com.singam.lionlibrary.domain.repository.TrackPreferenceRepository
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

private val Context.trackPreferenceStore: DataStore<Preferences> by
    preferencesDataStore(name = "lionlibrary_track_preferences")

/** One small JSON entry per title; a missing or unreadable entry means "use the defaults". */
class DataStoreTrackPreferenceRepository(context: Context) : TrackPreferenceRepository {
    private val store = context.trackPreferenceStore
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun get(mediaId: Long): TrackPreference? {
        val encoded =
            try {
                store.data.first()[key(mediaId)]
            } catch (_: IOException) {
                null
            } ?: return null
        return try {
            json.decodeFromString<TrackPreference>(encoded)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    override suspend fun save(mediaId: Long, preference: TrackPreference) {
        try {
            store.edit { it[key(mediaId)] = json.encodeToString(preference) }
        } catch (_: IOException) {
            // Losing a remembered track only means the next file opens with the defaults.
        }
    }

    private fun key(mediaId: Long) = stringPreferencesKey("media_$mediaId")
}
