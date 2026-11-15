package com.singam.lionlibrary.domain.repository

import com.singam.lionlibrary.domain.model.DataError
import com.singam.lionlibrary.domain.model.OperationResult

interface MetadataRepository {
    suspend fun refreshArtwork(): OperationResult<Int, DataError>

    suspend fun validateKey(key: String): OperationResult<Unit, DataError>
}
