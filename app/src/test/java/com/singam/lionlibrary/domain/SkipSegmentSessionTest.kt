package com.singam.lionlibrary.domain

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.*
import com.singam.lionlibrary.domain.model.*
import com.singam.lionlibrary.domain.model.SkipSegmentType.CREDITS
import com.singam.lionlibrary.domain.model.SkipSegmentType.INTRO
import com.singam.lionlibrary.domain.repository.SkipSegmentRepository
import com.singam.lionlibrary.domain.usecase.SkipSegmentSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * Break vectors: a lookup before any duration, chapters that already cover one or both types, the
 * switch off at start, turned off mid-flight and back on, a slow answer arriving after the file
 * changed, provider failure, and duration changes at, below and above the tolerance (repeatedly).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SkipSegmentSessionTest {
    private val key = SkipLookupKey(1396, false, 1, 1)
    private val hour = 3_600_000L
    private val onlineIntro = SkipSegment(INTRO, 0, 90_000, SkipSource.THEINTRODB, 0.9)
    private val onlineCredits = SkipSegment(CREDITS, hour - 120_000, null, SkipSource.SKIPDB, 0.8)

    private class Request(
        val durationMs: Long,
        val types: Set<SkipSegmentType>,
        val answer: CompletableDeferred<OperationResult<List<SkipSegment>, DataError>>,
    )

    private class FakeRepository : SkipSegmentRepository {
        val requests = mutableListOf<Request>()
        var cancelled = 0

        override suspend fun onlineSegments(
            key: SkipLookupKey,
            durationMs: Long,
            types: Set<SkipSegmentType>,
        ): OperationResult<List<SkipSegment>, DataError> {
            val request = Request(durationMs, types, CompletableDeferred())
            requests += request
            try {
                return request.answer.await()
            } catch (e: kotlinx.coroutines.CancellationException) {
                cancelled++
                throw e
            }
        }
    }

    private fun TestScope.session(repository: FakeRepository, online: Boolean = true) =
        SkipSegmentSession(repository, backgroundScope).apply { setOnlineEnabled(online) }

    @Test
    fun `no lookup starts until a positive duration is known`() =
        runTest(UnconfinedTestDispatcher()) {
            val repository = FakeRepository()
            val session = session(repository)
            session.start(key)
            session.onPlayback(0, emptyList())
            session.onPlayback(-1, emptyList())
            assertThat(repository.requests).isEmpty()
            session.onPlayback(hour, emptyList())
            assertThat(repository.requests.single().types).isEqualTo(setOf(INTRO, CREDITS))
        }

    @Test
    fun `an online answer is published, and a repeated duration does not ask again`() =
        runTest(UnconfinedTestDispatcher()) {
            val repository = FakeRepository()
            val session = session(repository)
            session.segments.test {
                assertThat(awaitItem()).isEmpty()
                session.start(key)
                session.onPlayback(hour, emptyList())
                repository.requests
                    .single()
                    .answer
                    .complete(OperationResult.Success(listOf(onlineIntro)))
                assertThat(awaitItem()).containsExactly(onlineIntro)
                session.onPlayback(hour, emptyList())
                assertThat(repository.requests).hasSize(1)
            }
        }

    @Test
    fun `types already named by chapters are not requested online`() =
        runTest(UnconfinedTestDispatcher()) {
            val repository = FakeRepository()
            val session = session(repository)
            session.start(key)
            session.onPlayback(hour, listOf(PlaybackChapter(30_000, 120_000, "Opening")))
            assertThat(repository.requests.single().types).isEqualTo(setOf(CREDITS))
        }

    @Test
    fun `chapters for both types need no online request at all`() =
        runTest(UnconfinedTestDispatcher()) {
            val repository = FakeRepository()
            val session = session(repository)
            session.start(key)
            session.onPlayback(
                hour,
                listOf(
                    PlaybackChapter(30_000, 120_000, "OP"),
                    PlaybackChapter(hour - 90_000, null, "ED"),
                ),
            )
            assertThat(repository.requests).isEmpty()
            assertThat(session.segments.value.map { it.source }).containsOnly(SkipSource.CHAPTER)
        }

    @Test
    fun `files without an online identity use chapters only`() =
        runTest(UnconfinedTestDispatcher()) {
            val repository = FakeRepository()
            val session = session(repository)
            session.start(null)
            session.onPlayback(hour, listOf(PlaybackChapter(30_000, 120_000, "Intro")))
            assertThat(repository.requests).isEmpty()
            assertThat(session.segments.value).hasSize(1)
        }

    @Test
    fun `with the switch off nothing is requested, and turning it on starts the lookup`() =
        runTest(UnconfinedTestDispatcher()) {
            val repository = FakeRepository()
            val session = session(repository, online = false)
            session.start(key)
            session.onPlayback(hour, emptyList())
            assertThat(repository.requests).isEmpty()
            session.setOnlineEnabled(true)
            assertThat(repository.requests).hasSize(1)
        }

    @Test
    fun `turning the switch off cancels the lookup in flight and removes online buttons`() =
        runTest(UnconfinedTestDispatcher()) {
            val repository = FakeRepository()
            val session = session(repository)
            session.start(key)
            session.onPlayback(hour, listOf(PlaybackChapter(30_000, 120_000, "OP")))
            session.setOnlineEnabled(false)
            assertThat(repository.cancelled).isEqualTo(1)
            assertThat(session.segments.value.map { it.source }).containsOnly(SkipSource.CHAPTER)

            session.setOnlineEnabled(true)
            repository.requests
                .last()
                .answer
                .complete(OperationResult.Success(listOf(onlineCredits)))
            assertThat(session.segments.value).contains(onlineCredits)
            session.setOnlineEnabled(false)
            assertThat(session.segments.value).doesNotContain(onlineCredits)
        }

    @Test
    fun `a late answer for the previous file is discarded`() =
        runTest(UnconfinedTestDispatcher()) {
            val repository = FakeRepository()
            val session = session(repository)
            session.start(key)
            session.onPlayback(hour, emptyList())
            val stale = repository.requests.single().answer
            session.start(key.copy(episode = 2))
            // Completing a cancelled request must not leak its answer into the new file.
            stale.complete(OperationResult.Success(listOf(onlineIntro)))
            assertThat(session.segments.value).isEmpty()
            assertThat(repository.cancelled).isEqualTo(1)
        }

    @Test
    fun `a failed lookup leaves chapter buttons in place`() =
        runTest(UnconfinedTestDispatcher()) {
            val repository = FakeRepository()
            val session = session(repository)
            session.start(key)
            session.onPlayback(hour, listOf(PlaybackChapter(30_000, 120_000, "OP")))
            repository.requests.single().answer.complete(OperationResult.Failure(DataError.OFFLINE))
            assertThat(session.segments.value).hasSize(1)
        }

    @Test
    fun `a duration change beyond two seconds revalidates exactly once`() =
        runTest(UnconfinedTestDispatcher()) {
            val repository = FakeRepository()
            val session = session(repository)
            session.start(key)
            session.onPlayback(hour, emptyList())
            repository.requests
                .single()
                .answer
                .complete(OperationResult.Success(listOf(onlineIntro)))
            session.onPlayback(hour + 2_000, emptyList())
            assertThat(repository.requests).hasSize(1)
            session.onPlayback(hour + 60_000, emptyList())
            assertThat(repository.requests).hasSize(2)
            assertThat(repository.requests.last().durationMs).isEqualTo(hour + 60_000)
            // The old answer belonged to the old duration.
            assertThat(session.segments.value).isEmpty()
            session.onPlayback(hour + 600_000, emptyList())
            assertThat(repository.requests).hasSize(2)
        }
}
