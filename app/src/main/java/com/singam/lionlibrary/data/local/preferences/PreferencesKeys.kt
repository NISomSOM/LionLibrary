package com.singam.lionlibrary.data.local.preferences

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

object PreferencesKeys {
    val KEY_VERIFIED = booleanPreferencesKey("key_verified")
    val SETUP_DISMISSED = booleanPreferencesKey("setup_dismissed")
    val SCAN_SUMMARY = stringPreferencesKey("scan_summary")

    val TMDB_API_KEY = stringPreferencesKey("tmdb_api_key")
    val MOVIES_FOLDER_URI = stringPreferencesKey("movies_folder_uri")
    val SHOWS_FOLDER_URI = stringPreferencesKey("shows_folder_uri")

    val LAST_SCAN_TIME = longPreferencesKey("last_scan_time")

    val FORCE_LIBVLC = booleanPreferencesKey("force_libvlc")
    val FORCE_EXTERNAL_PLAYER = booleanPreferencesKey("force_external_player")
    val ONLINE_SKIP_TIMES = booleanPreferencesKey("online_skip_times")
}
