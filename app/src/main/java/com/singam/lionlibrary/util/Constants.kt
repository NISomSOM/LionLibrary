package com.singam.lionlibrary.util

object Constants {

    // Supported video formats
    val SUPPORTED_VIDEO_EXTENSIONS = setOf(
        "mkv", "mp4", "avi", "mov", "m4v", "webm"
    )

    // Supported subtitle formats
    val SUBTITLE_EXTENSIONS = setOf("srt", "vtt", "ass")

    // TMDB API endpoints
    const val TMDB_BASE_URL = "https://api.themoviedb.org/3/"
    const val TMDB_IMAGE_BASE_URL_W500 = "https://image.tmdb.org/t/p/w500"
    const val TMDB_IMAGE_BASE_URL_W780 = "https://image.tmdb.org/t/p/w780"
    const val TMDB_IMAGE_BASE_URL_W1280 = "https://image.tmdb.org/t/p/w1280"
    const val TMDB_IMAGE_BASE_URL_ORIGINAL = "https://image.tmdb.org/t/p/original"

    // Minimum confidence score for a TMDB match
    const val MATCH_CONFIDENCE_THRESHOLD = 0.80f

    // Concurrent files processed during library scan
    const val SCAN_CONCURRENCY = 8



    // Image cache folder name
    const val IMAGE_CACHE_DIR = "posters"
}

