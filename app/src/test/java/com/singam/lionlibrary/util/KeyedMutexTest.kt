package com.singam.lionlibrary.util

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class KeyedMutexTest {
    // Break vectors: same key must serialize (a waiter must not get a fresh mutex when the holder
    // releases), different keys must not block each other, a throwing block must release its
    // entry, cancellation while waiting must release its entry, and the map must be empty when
    // idle (the leak this replaces).

    @Test
    fun `same key serializes even while a waiter is queued during release`() =
        runTest(UnconfinedTestDispatcher()) {
            val locks = KeyedMutex<String>()
            val order = mutableListOf<String>()
            val gate = CompletableDeferred<Unit>()
            val first = launch {
                locks.withLock("poster") {
                    order += "first-start"
                    gate.await()
                    order += "first-end"
                }
            }
            val second = launch { locks.withLock("poster") { order += "second" } }
            val third = launch { locks.withLock("poster") { order += "third" } }

            assertThat(locks.activeKeys).isEqualTo(1)
            gate.complete(Unit)
            first.join()
            second.join()
            third.join()

            assertThat(order).containsExactly("first-start", "first-end", "second", "third")
            assertThat(locks.activeKeys).isEqualTo(0)
        }

    @Test
    fun `different keys do not wait for each other`() =
        runTest(UnconfinedTestDispatcher()) {
            val locks = KeyedMutex<Int>()
            val gate = CompletableDeferred<Unit>()
            val order = mutableListOf<Int>()
            val holder = launch { locks.withLock(1) { gate.await() } }

            locks.withLock(2) { order += 2 }

            assertThat(order).containsExactly(2)
            gate.complete(Unit)
            holder.join()
            assertThat(locks.activeKeys).isEqualTo(0)
        }

    @Test
    fun `throwing block releases its entry`() = runTest {
        val locks = KeyedMutex<String>()

        assertFailure { locks.withLock("x") { error("boom") } }
            .isInstanceOf(IllegalStateException::class)

        assertThat(locks.activeKeys).isEqualTo(0)
        assertThat(locks.withLock("x") { "again" }).isEqualTo("again")
    }

    @Test
    fun `cancelled waiter releases its entry`() =
        runTest(UnconfinedTestDispatcher()) {
            val locks = KeyedMutex<String>()
            val gate = CompletableDeferred<Unit>()
            val holder = launch { locks.withLock("k") { gate.await() } }
            val waiter = launch { locks.withLock("k") { error("must not run") } }

            waiter.cancel()
            gate.complete(Unit)
            holder.join()

            assertThat(locks.activeKeys).isEqualTo(0)
        }
}
