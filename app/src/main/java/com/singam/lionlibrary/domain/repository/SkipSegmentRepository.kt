package com.singam.lionlibrary.domain.repository

import com.singam.lionlibrary.domain.model.DataError
import com.singam.lionlibrary.domain.model.OperationResult
import com.singam.lionlibrary.domain.model.SkipLookupKey
import com.singam.lionlibrary.domain.model.SkipSegment
import com.singam.lionlibrary.domain.model.SkipSegmentType

/**
 * Online skip times (TheIntroDB first, SkipDB for types still missing). Results are validated and
 * eligible only. One instance lives for one player session and caches in memory; nothing is written
 * to disk. File chapters are read by the player engines, not here.
 */
interface SkipSegmentRepository {
    suspend fun onlineSegments(
        key: SkipLookupKey,
        durationMs: Long,
        types: Set<SkipSegmentType>,
    ): OperationResult<List<SkipSegment>, DataError>
}
