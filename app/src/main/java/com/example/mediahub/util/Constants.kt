package com.example.mediahub.util

object Constants {

    // Supported video file extensions
    val SUPPORTED_VIDEO_EXTENSIONS = setOf(
        "mkv", "mp4", "avi", "mov", "m4v", "webm"
    )

    // TMDB
    const val TMDB_BASE_URL = "https://api.themoviedb.org/3/"
    const val TMDB_IMAGE_BASE_URL_W500 = "https://image.tmdb.org/t/p/w500"
    const val TMDB_IMAGE_BASE_URL_W780 = "https://image.tmdb.org/t/p/w780"
    const val TMDB_IMAGE_BASE_URL_W1280 = "https://image.tmdb.org/t/p/w1280"
    const val TMDB_IMAGE_BASE_URL_ORIGINAL = "https://image.tmdb.org/t/p/original"

    // Confidence threshold for TMDB matching
    const val MATCH_CONFIDENCE_THRESHOLD = 0.80f



    // Image cache directory name
    const val IMAGE_CACHE_DIR = "posters"
}
