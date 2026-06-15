package com.example.mediahub.data.local.preferences

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

object PreferencesKeys {
    val TMDB_API_KEY = stringPreferencesKey("tmdb_api_key")
    val MOVIES_FOLDER_URI = stringPreferencesKey("movies_folder_uri")
    val SHOWS_FOLDER_URI = stringPreferencesKey("shows_folder_uri")
    val ANIME_FOLDER_URI = stringPreferencesKey("anime_folder_uri")

    val LAST_SCAN_TIME = longPreferencesKey("last_scan_time")
    val IS_SETUP_COMPLETE = booleanPreferencesKey("is_setup_complete")
}
