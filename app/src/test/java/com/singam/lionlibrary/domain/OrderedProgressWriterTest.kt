package com.singam.lionlibrary.domain

import assertk.assertThat
import assertk.assertions.*
import com.singam.lionlibrary.domain.model.*
import com.singam.lionlibrary.domain.usecase.OrderedProgressWriter
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OrderedProgressWriterTest {
    @Test
    fun `delayed writes cannot adopt the next episode or overtake completion`() =
        runTest(UnconfinedTestDispatcher()) {
            val gate = CompletableDeferred<Unit>()
            val stored = mutableListOf<PlaybackSnapshot>()
            val writer =
                OrderedProgressWriter(backgroundScope) { snapshot ->
                    gate.await()
                    stored += snapshot
                    OperationResult.Success(Unit)
                }
            val first = PlaybackSnapshot(1, 10, 500, 1000, recordedAt = 1)
            val final = first.copy(ended = true, recordedAt = 2)
            val next = first.copy(episodeId = 11, positionMs = 20, recordedAt = 3)
            val a = writer.submit(first)
            val b = writer.submit(final)
            val c = writer.submit(next)
            assertThat(stored).isEmpty()
            gate.complete(Unit)
            a.await()
            b.await()
            c.await()
            assertThat(stored).isEqualTo(listOf(first, final, next))
        }

    @Test
    fun `failed persistence is observable and does not poison later writes`() =
        runTest(UnconfinedTestDispatcher()) {
            var calls = 0
            val writer =
                OrderedProgressWriter(backgroundScope) {
                    if (++calls == 1) throw IllegalStateException()
                    else OperationResult.Success(Unit)
                }
            val snapshot = PlaybackSnapshot(1, 2, 50, 100, recordedAt = 1)
            assertThat(writer.submit(snapshot).await())
                .isEqualTo(OperationResult.Failure(DataError.STORAGE))
            assertThat(writer.submit(snapshot).await()).isEqualTo(OperationResult.Success(Unit))
        }

    @Test
    fun `cancelling the requesting UI does not cancel an already queued exit save`() =
        runTest(UnconfinedTestDispatcher()) {
            val gate = CompletableDeferred<Unit>()
            val stored = mutableListOf<Long>()
            val writer =
                OrderedProgressWriter(backgroundScope) {
                    gate.await()
                    stored += it.episodeId
                    OperationResult.Success(Unit)
                }
            val result = writer.submit(PlaybackSnapshot(1, 4, 300, 1000, recordedAt = 1))
            result.cancel()
            gate.complete(Unit)
            assertThat(stored).isEqualTo(listOf(4L))
        }
}
