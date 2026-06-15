package com.example.mediahub.data.scanner

/**
 * Parsed result from a media filename.
 */
sealed class ParsedFile {
    data class Movie(val title: String, val year: Int?) : ParsedFile()
    data class Episode(val title: String, val season: Int, val episode: Int) : ParsedFile()
    data class Unknown(val rawName: String) : ParsedFile()
}
