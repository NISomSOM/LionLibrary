package com.singam.lionlibrary.data.repository

import com.singam.lionlibrary.data.remote.ImdbIdDataSource
import com.singam.lionlibrary.data.remote.SkipDbDataSource
import com.singam.lionlibrary.data.remote.TheIntroDbDataSource
import com.singam.lionlibrary.domain.model.DataError
import com.singam.lionlibrary.domain.model.OperationResult
import com.singam.lionlibrary.domain.model.SkipLookupKey
import com.singam.lionlibrary.domain.model.SkipSegment
import com.singam.lionlibrary.domain.model.SkipSegmentType
import com.singam.lionlibrary.domain.repository.SkipSegmentRepository
import java.util.concurrent.ConcurrentHashMap

/**
 * TheIntroDB first; SkipDB only for segment types TheIntroDB did not supply, and only when an IMDb
 * id is available. Koin creates one per player, so the memory cache of answers, misses and IMDb ids
 * lasts for that player session only. Failed lookups are not cached.
 */
class OnlineSkipSegmentRepository(
    private val theIntroDb: TheIntroDbDataSource,
    private val skipDb: SkipDbDataSource,
    private val imdbIds: ImdbIdDataSource,
) : SkipSegmentRepository {
    private data class Lookup(
        val key: SkipLookupKey,
        val durationMs: Long,
        val types: Set<SkipSegmentType>,
    )

    private data class Title(val tmdbId: Int, val isMovie: Boolean)

    private val answers = ConcurrentHashMap<Lookup, List<SkipSegment>>()
    // An empty string records "this title has no IMDb id".
    private val imdbCache = ConcurrentHashMap<Title, String>()

    override suspend fun onlineSegments(
        key: SkipLookupKey,
        durationMs: Long,
        types: Set<SkipSegmentType>,
    ): OperationResult<List<SkipSegment>, DataError> {
        if (durationMs <= 0 || types.isEmpty()) return OperationResult.Success(emptyList())
        val lookup = Lookup(key, durationMs, types)
        answers[lookup]?.let {
            return OperationResult.Success(it)
        }
        var failure: DataError? = null
        val found = mutableListOf<SkipSegment>()
        when (val primary = theIntroDb.segments(key, durationMs)) {
            is OperationResult.Success -> found += primary.data.filter { it.type in types }
            is OperationResult.Failure -> failure = primary.error
        }
        val missing = types - found.map { it.type }.toSet()
        if (missing.isNotEmpty()) {
            when (val imdb = imdbId(key)) {
                is OperationResult.Failure -> failure = failure ?: imdb.error
                is OperationResult.Success ->
                    imdb.data?.let { id ->
                        when (val fallback = skipDb.segments(id, key, durationMs)) {
                            is OperationResult.Success ->
                                found += fallback.data.filter { it.type in missing }
                            is OperationResult.Failure -> failure = failure ?: fallback.error
                        }
                    }
            }
        }
        if (failure == null) answers[lookup] = found.toList()
        return if (failure != null && found.isEmpty()) OperationResult.Failure(failure)
        else OperationResult.Success(found.toList())
    }

    private suspend fun imdbId(key: SkipLookupKey): OperationResult<String?, DataError> {
        val title = Title(key.tmdbId, key.isMovie)
        imdbCache[title]?.let {
            return OperationResult.Success(it.ifEmpty { null })
        }
        val result = imdbIds.imdbId(key)
        if (result is OperationResult.Success) imdbCache[title] = result.data.orEmpty()
        return result
    }
}
