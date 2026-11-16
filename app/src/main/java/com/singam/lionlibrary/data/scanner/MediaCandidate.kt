package com.singam.lionlibrary.data.scanner

import android.net.Uri
import com.singam.lionlibrary.domain.model.MediaType

sealed interface MediaCandidate {
    data class Movie(
        val sourceUri: Uri,
        val title: String,
        val year: Int?,
        val subtitleUri: Uri? = null,
    ) : MediaCandidate

    data class Show(
        val title: String, // From folder names, not filenames.
        val seasons: Map<Int, List<EpisodeFile>>,
    ) : MediaCandidate

    data class Unknown(
        val sourceUri: Uri,
        val rawName: String,
        val reason: String,
        val expectedType: MediaType,
    ) : MediaCandidate
}

data class EpisodeFile(
    val uri: Uri,
    val episodeNumber: Int,
    val subtitleUri: Uri? = null,
    val episodeEndNumber: Int? = null,
    val extraTitle: String? =
        null, // Set for extras (trailers, NCOP/NCED); skips TMDB episode metadata.
)
