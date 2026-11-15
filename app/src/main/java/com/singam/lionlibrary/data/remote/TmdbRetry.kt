package com.singam.lionlibrary.data.remote

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import retrofit2.HttpException

/**
 * Backoff for TMDB calls made by the scanner and matcher. Rate limits (429), server errors (5xx)
 * and I/O failures are retried; anything else (401, 404, other 4xx, parsing) is thrown at once.
 * [maxAttempts] counts the first call, so the total time waited is bounded.
 */
internal data class TmdbRetryPolicy(
    val maxAttempts: Int = 5,
    val initialDelayMs: Long = 2_000L,
    /** Growth after a 429. Details calls use 1.5; the matcher's searches use 2. */
    val rateLimitMultiplier: Double = 2.0,
    /** Growth after a 5xx or I/O failure. */
    val failureMultiplier: Double = 2.0,
) {
    companion object {
        /** Scanner details/season/images calls. */
        val Details = TmdbRetryPolicy(rateLimitMultiplier = 1.5)

        /** Matcher search and alias calls. */
        val Matcher = TmdbRetryPolicy()
    }
}

internal suspend fun <T> retryTmdb(
    policy: TmdbRetryPolicy,
    sleep: suspend (Long) -> Unit = { delay(it) },
    block: suspend () -> T,
): T {
    var currentDelay = policy.initialDelayMs
    repeat((policy.maxAttempts - 1).coerceAtLeast(0)) {
        val multiplier =
            try {
                return block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: HttpException) {
                when {
                    e.code() == 429 -> policy.rateLimitMultiplier
                    e.code() >= 500 -> policy.failureMultiplier
                    else -> throw e
                }
            } catch (_: IOException) {
                policy.failureMultiplier
            }
        sleep(currentDelay)
        currentDelay = (currentDelay * multiplier).toLong()
    }
    return block()
}
