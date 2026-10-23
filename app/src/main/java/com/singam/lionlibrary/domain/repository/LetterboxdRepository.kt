package com.singam.lionlibrary.domain.repository

import com.singam.lionlibrary.domain.model.MediaItem

interface LetterboxdRepository {

    // Refreshes the cached Letterboxd rating for a movie; null if not applicable.
    suspend fun refreshRating(mediaId: Long): MediaItem?

    fun filmUrl(tmdbId: Int): String
}
