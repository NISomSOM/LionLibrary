package com.singam.lionlibrary.data.remote

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

/**
 * Break vectors: seconds, HTTP dates, malformed/negative/huge values, a date in the past,
 * overlapping cooldowns where a shorter one must not shorten a longer one, and clock progress.
 */
class TmdbRateLimitTest {
    @Test
    fun `seconds and HTTP dates are honoured and bounded`() {
        assertThat(retryAfterDelayMs("3", nowMs = 0)).isEqualTo(3_000L)
        assertThat(retryAfterDelayMs("999999999999999", nowMs = 0)).isEqualTo(30_000L)
        val now = 784_111_777_000L // Sun, 06 Nov 1994 08:49:37 GMT
        assertThat(retryAfterDelayMs("Sun, 06 Nov 1994 08:49:42 GMT", now)).isEqualTo(5_000L)
    }

    @Test
    fun `missing, malformed, negative and past values fall back`() {
        assertThat(retryAfterDelayMs(null, 0)).isEqualTo(2_000L)
        assertThat(retryAfterDelayMs("soon 🚀", 0)).isEqualTo(2_000L)
        assertThat(retryAfterDelayMs("-5", 0)).isEqualTo(2_000L)
        assertThat(retryAfterDelayMs("Sun, 06 Nov 1994 08:49:37 GMT", 784_111_999_000L))
            .isEqualTo(2_000L)
    }

    @Test
    fun `a shorter cooldown never shortens a longer one, and time drains it`() {
        var now = 1_000L
        val limiter = TmdbRateLimiter { now }
        limiter.record("10")
        limiter.record("1")
        assertThat(limiter.remainingMs()).isEqualTo(10_000L)
        now += 9_500
        assertThat(limiter.remainingMs()).isEqualTo(500L)
        now += 1_000
        assertThat(limiter.remainingMs()).isEqualTo(0L)
    }
}
