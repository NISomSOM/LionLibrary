package com.singam.lionlibrary.domain.repository

import com.singam.lionlibrary.domain.model.*

data class MetadataCandidate(
    val tmdbId: Int,
    val title: String,
    val year: Int?,
    val type: MediaType,
)

data class EpisodeAssignment(val season: Int, val episode: Int)

interface MetadataRepository {
    suspend fun refreshArtwork(): OperationResult<Int, DataError>

    suspend fun validateKey(key: String): OperationResult<Unit, DataError>

    suspend fun search(
        title: String,
        type: MediaType,
    ): OperationResult<List<MetadataCandidate>, DataError>

    suspend fun identify(
        mediaId: Long,
        candidate: MetadataCandidate,
        assignment: EpisodeAssignment? = null,
    ): OperationResult<Unit, DataError>

    suspend fun retry(mediaId: Long): OperationResult<Unit, DataError>

    suspend fun checkAvailability(): OperationResult<Set<Long>, DataError>
}
