package com.singam.lionlibrary.data.repository

import assertk.assertThat
import assertk.assertions.*
import com.singam.lionlibrary.data.remote.ImdbIdDataSource
import com.singam.lionlibrary.data.remote.SkipDbDataSource
import com.singam.lionlibrary.data.remote.TheIntroDbDataSource
import com.singam.lionlibrary.domain.model.*
import com.singam.lionlibrary.domain.model.SkipSegmentType.CREDITS
import com.singam.lionlibrary.domain.model.SkipSegmentType.INTRO
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * Break vectors: TheIntroDB answering everything (SkipDB must stay silent), one type only, an
 * unrequested type in the answer, no IMDb id (no TMDB key), each provider failing alone and both
 * failing, repeated identical lookups (cache), failures that must not be cached, and episodes of
 * one show sharing the show-level IMDb id.
 */
class OnlineSkipSegmentRepositoryTest {
    private val hour = 3_600_000L
    private val episode = SkipLookupKey(1396, false, 1, 1)
    private val intro = SkipSegment(INTRO, 0, 60_000, SkipSource.THEINTRODB, 0.9)
    private val credits = SkipSegment(CREDITS, hour - 90_000, null, SkipSource.THEINTRODB, 0.9)
    private val skipDbIntro = SkipSegment(INTRO, 1_000, 70_000, SkipSource.SKIPDB, 0.8)
    private val skipDbCredits = SkipSegment(CREDITS, hour - 80_000, null, SkipSource.SKIPDB, 0.8)
    private val both = setOf(INTRO, CREDITS)

    private class Fakes {
        var theIntroDb: OperationResult<List<SkipSegment>, DataError> =
            OperationResult.Success(emptyList())
        var skipDb: OperationResult<List<SkipSegment>, DataError> =
            OperationResult.Success(emptyList())
        var imdb: OperationResult<String?, DataError> = OperationResult.Success("tt0903747")
        var theIntroDbCalls = 0
        var skipDbCalls = 0
        var imdbCalls = 0

        val repository =
            OnlineSkipSegmentRepository(
                object : TheIntroDbDataSource {
                    override suspend fun segments(key: SkipLookupKey, durationMs: Long) =
                        theIntroDb.also {
                            theIntroDbCalls++
                        }
                },
                object : SkipDbDataSource {
                    override suspend fun segments(
                        imdbId: String,
                        key: SkipLookupKey,
                        durationMs: Long,
                    ) = skipDb.also { skipDbCalls++ }
                },
                object : ImdbIdDataSource {
                    override suspend fun imdbId(key: SkipLookupKey) = imdb.also { imdbCalls++ }
                },
            )
    }

    @Test
    fun `a complete TheIntroDB answer never contacts TMDB or SkipDB`() = runTest {
        val fakes = Fakes().apply { theIntroDb = OperationResult.Success(listOf(intro, credits)) }
        assertThat(fakes.repository.onlineSegments(episode, hour, both))
            .isEqualTo(OperationResult.Success(listOf(intro, credits)))
        assertThat(fakes.imdbCalls).isEqualTo(0)
        assertThat(fakes.skipDbCalls).isEqualTo(0)
    }

    @Test
    fun `SkipDB only fills the type TheIntroDB lacks`() = runTest {
        val fakes =
            Fakes().apply {
                theIntroDb = OperationResult.Success(listOf(intro))
                skipDb = OperationResult.Success(listOf(skipDbIntro, skipDbCredits))
            }
        assertThat(fakes.repository.onlineSegments(episode, hour, both))
            .isEqualTo(OperationResult.Success(listOf(intro, skipDbCredits)))
    }

    @Test
    fun `types that were not requested are dropped`() = runTest {
        val fakes = Fakes().apply { theIntroDb = OperationResult.Success(listOf(intro, credits)) }
        assertThat(fakes.repository.onlineSegments(episode, hour, setOf(CREDITS)))
            .isEqualTo(OperationResult.Success(listOf(credits)))
    }

    @Test
    fun `without an IMDb id (no TMDB key) SkipDB is skipped`() = runTest {
        val fakes =
            Fakes().apply {
                theIntroDb = OperationResult.Success(listOf(intro))
                imdb = OperationResult.Success(null)
            }
        assertThat(fakes.repository.onlineSegments(episode, hour, both))
            .isEqualTo(OperationResult.Success(listOf(intro)))
        assertThat(fakes.skipDbCalls).isEqualTo(0)
    }

    @Test
    fun `a TheIntroDB failure still lets SkipDB answer`() = runTest {
        val fakes =
            Fakes().apply {
                theIntroDb = OperationResult.Failure(DataError.SERVER)
                skipDb = OperationResult.Success(listOf(skipDbCredits))
            }
        assertThat(fakes.repository.onlineSegments(episode, hour, both))
            .isEqualTo(OperationResult.Success(listOf(skipDbCredits)))
    }

    @Test
    fun `nothing found because of failures is reported as a failure`() = runTest {
        val fakes =
            Fakes().apply {
                theIntroDb = OperationResult.Failure(DataError.OFFLINE)
                imdb = OperationResult.Failure(DataError.UNAUTHORIZED)
            }
        assertThat(fakes.repository.onlineSegments(episode, hour, both))
            .isEqualTo(OperationResult.Failure(DataError.OFFLINE))
    }

    @Test
    fun `answers and misses are cached for the session`() = runTest {
        val fakes = Fakes()
        repeat(3) { fakes.repository.onlineSegments(episode, hour, both) }
        assertThat(fakes.theIntroDbCalls).isEqualTo(1)
        assertThat(fakes.skipDbCalls).isEqualTo(1)
    }

    @Test
    fun `failed lookups are not cached`() = runTest {
        val fakes = Fakes().apply { theIntroDb = OperationResult.Failure(DataError.SERVER) }
        fakes.repository.onlineSegments(episode, hour, both)
        fakes.theIntroDb = OperationResult.Success(listOf(intro))
        assertThat(fakes.repository.onlineSegments(episode, hour, both))
            .isEqualTo(OperationResult.Success(listOf(intro)))
        assertThat(fakes.theIntroDbCalls).isEqualTo(2)
    }

    @Test
    fun `episodes of one show share one IMDb lookup, including a miss`() = runTest {
        val fakes = Fakes().apply { imdb = OperationResult.Success(null) }
        fakes.repository.onlineSegments(episode, hour, both)
        fakes.repository.onlineSegments(episode.copy(episode = 2), hour, both)
        assertThat(fakes.imdbCalls).isEqualTo(1)
        // A movie with the same numeric id is another TMDB namespace.
        fakes.repository.onlineSegments(SkipLookupKey(1396, isMovie = true), hour, both)
        assertThat(fakes.imdbCalls).isEqualTo(2)
    }

    @Test
    fun `an unknown duration or no requested types asks nobody`() = runTest {
        val fakes = Fakes()
        fakes.repository.onlineSegments(episode, 0, both)
        fakes.repository.onlineSegments(episode, hour, emptySet())
        assertThat(fakes.theIntroDbCalls).isEqualTo(0)
    }
}
