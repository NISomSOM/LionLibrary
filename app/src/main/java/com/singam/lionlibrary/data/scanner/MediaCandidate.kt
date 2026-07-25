package com.singam.lionlibrary.data.scanner

import android.net.Uri

sealed interface MediaCandidate {
    data class Movie(
        val sourceUri: Uri,
        val title: String,
        val year: Int?,
        val subtitleUri: Uri? = null
    ) : MediaCandidate

    data class Show(
        val title: String,              // Extracted from folder names, never filenames
        val seasons: Map<Int, List<EpisodeFile>>
    ) : MediaCandidate

    data class Unknown(
        val sourceUri: Uri,
        val rawName: String,
        val reason: String,
        val expectedType: com.singam.lionlibrary.domain.model.MediaType
    ) : MediaCandidate
}

data class EpisodeFile(
    val uri: Uri,
    val episodeNumber: Int,
    val subtitleUri: Uri? = null
)
