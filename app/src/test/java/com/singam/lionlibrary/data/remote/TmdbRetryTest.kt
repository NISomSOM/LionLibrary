package com.singam.lionlibrary.data.remote

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Test
import retrofit2.HttpException
import retrofit2.Response

/**
 * Break vectors: the documented schedules (details 1.5x after 429, 2x after 5xx/I/O; matcher 2x), a
 * mix of failures, non-retryable codes thrown without waiting, the final attempt's error surfacing,
 * cancellation never retried, and a degenerate attempt count.
 */
class TmdbRetryTest {
    private fun http(code: Int) = HttpException(Response.error<Any>(code, "".toResponseBody()))

    private suspend fun run(
        policy: TmdbRetryPolicy,
        failures: List<Throwable>,
        waits: MutableList<Long>,
    ): String {
        var call = 0
        return retryTmdb(policy, sleep = { waits += it }) {
            failures.getOrNull(call++)?.let { throw it }
            "ok"
        }
    }

    @Test
    fun `details rate limits back off by one and a half`() = runTest {
        val waits = mutableListOf<Long>()

        val result = run(TmdbRetryPolicy.Details, List(4) { http(429) }, waits)

        assertThat(result).isEqualTo("ok")
        assertThat(waits).isEqualTo(listOf(2_000L, 3_000L, 4_500L, 6_750L))
    }

    @Test
    fun `details server and io failures double and mix with rate limits`() = runTest {
        val waits = mutableListOf<Long>()

        run(TmdbRetryPolicy.Details, listOf(http(503), IOException(), http(429)), waits)

        assertThat(waits).isEqualTo(listOf(2_000L, 4_000L, 8_000L))
    }

    @Test
    fun `matcher doubles after rate limits`() = runTest {
        val waits = mutableListOf<Long>()

        run(TmdbRetryPolicy.Matcher, List(3) { http(429) }, waits)

        assertThat(waits).isEqualTo(listOf(2_000L, 4_000L, 8_000L))
    }

    @Test
    fun `non retryable codes are thrown without waiting`() = runTest {
        for (code in listOf(401, 404, 422)) {
            val waits = mutableListOf<Long>()
            assertFailure { run(TmdbRetryPolicy.Details, listOf(http(code)), waits) }
                .isInstanceOf(HttpException::class)
            assertThat(waits).isEqualTo(emptyList<Long>())
        }
    }

    @Test
    fun `fifth failure surfaces after four waits`() = runTest {
        val waits = mutableListOf<Long>()

        assertFailure { run(TmdbRetryPolicy.Matcher, List(5) { IOException("down") }, waits) }
            .isInstanceOf(IOException::class)
        assertThat(waits.size).isEqualTo(4)
    }

    @Test
    fun `cancellation is not retried`() = runTest {
        val waits = mutableListOf<Long>()

        assertFailure { run(TmdbRetryPolicy.Details, listOf(CancellationException()), waits) }
            .isInstanceOf(CancellationException::class)
        assertThat(waits).isEqualTo(emptyList<Long>())
    }

    @Test
    fun `single attempt policy calls once`() = runTest {
        val waits = mutableListOf<Long>()

        assertFailure { run(TmdbRetryPolicy(maxAttempts = 1), listOf(http(500)), waits) }
            .isInstanceOf(HttpException::class)
        assertThat(waits).isEqualTo(emptyList<Long>())
    }
}
