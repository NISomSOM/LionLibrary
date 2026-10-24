package com.singam.lionlibrary.util

object Constants {

    // Video extensions.
    val SUPPORTED_VIDEO_EXTENSIONS = setOf("mkv", "mp4", "avi", "mov", "m4v", "webm")

    // Subtitle extensions.
    val SUBTITLE_EXTENSIONS = setOf("srt", "vtt", "ass")

    // TMDB URLs.
    const val TMDB_BASE_URL = "https://api.themoviedb.org/3/"
    const val TMDB_IMAGE_BASE_URL_W500 = "https://image.tmdb.org/t/p/w500"
    const val TMDB_IMAGE_BASE_URL_W780 = "https://image.tmdb.org/t/p/w780"
    const val TMDB_IMAGE_BASE_URL_W1280 = "https://image.tmdb.org/t/p/w1280"
    const val TMDB_IMAGE_BASE_URL_ORIGINAL = "https://image.tmdb.org/t/p/original"

    // Skip-time providers (keyless reads).
    const val THEINTRODB_BASE_URL = "https://api.theintrodb.org/v4/"
    const val SKIPDB_BASE_URL = "https://api.skipdb.tv/"

    // Match threshold.
    const val MATCH_CONFIDENCE_THRESHOLD = 0.80f

    // Scan concurrency.
    const val SCAN_CONCURRENCY = 8

    // Nesting limits for folder traversal.
    const val MAX_MOVIE_FOLDER_DEPTH = 4
    const val MAX_SHOW_WRAPPER_DEPTH = 2

    // Cache dir.
    const val IMAGE_CACHE_DIR = "posters"
}
