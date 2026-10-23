package com.singam.lionlibrary.domain.repository

import com.singam.lionlibrary.domain.model.AppError
import com.singam.lionlibrary.domain.model.OperationResult

data class DeletionTarget(val mediaId: Long, val episodeId: Long? = null)

/** A single-use in-process preview. Only its issuing repository may execute it. */
data class DeletionPreview(
    val token: String,
    val title: String,
    val location: String,
    val isFolder: Boolean,
    val fileCount: Int,
    val combinedEpisode: Boolean = false,
)

enum class DeletionError : AppError {
    BUSY,
    UNAVAILABLE,
    UNSAFE_TARGET,
    CHANGED,
    DELETE_FAILED,
}

data class DeletionOutcome(val removedMediaIds: Set<Long>)

interface MediaDeletionRepository {
    suspend fun preview(target: DeletionTarget): OperationResult<DeletionPreview, DeletionError>

    suspend fun delete(preview: DeletionPreview): OperationResult<DeletionOutcome, DeletionError>
}
