package com.singam.lionlibrary.data.remote

import assertk.assertThat
import assertk.assertions.isEqualTo
import okhttp3.Headers.Companion.headersOf
import org.junit.jupiter.api.Test

/**
 * Break vectors: a zero or missing Retry-After (must still back off), a huge one (bounded), a
 * shorter later cooldown that must not shorten a longer one, and a clock moving past the end.
 */
class ProviderRequestGateTest {
    private var now = 0L
    private val gate = ProviderRequestGate(minIntervalMs = 0, clock = { now })

    @Test
    fun `zero and missing delays still back off for two seconds`() {
        gate.recordRateLimit(headersOf("Retry-After", "0"))
        assertThat(gate.cooldownRemainingMs()).isEqualTo(2_000L)
        now += 2_000
        gate.recordRateLimit(headersOf())
        assertThat(gate.cooldownRemainingMs()).isEqualTo(2_000L)
    }

    @Test
    fun `huge delays are bounded to ten minutes`() {
        gate.recordRateLimit(headersOf("X-UsageLimit-Reset", "86400"))
        assertThat(gate.cooldownRemainingMs()).isEqualTo(600_000L)
    }

    @Test
    fun `a shorter cooldown never shortens a longer one, and time drains it`() {
        gate.recordRateLimit(headersOf("Retry-After", "60"))
        gate.recordRateLimit(headersOf("Retry-After", "5"))
        assertThat(gate.cooldownRemainingMs()).isEqualTo(60_000L)
        now += 61_000
        assertThat(gate.cooldownRemainingMs()).isEqualTo(0L)
    }
}
