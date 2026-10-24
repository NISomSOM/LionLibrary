package com.singam.lionlibrary.data.remote

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import okhttp3.Interceptor
import okhttp3.Response

private const val DefaultCooldownMs = 2_000L
private const val MaximumCooldownMs = 30_000L
private const val WaitSliceMs = 100L

/**
 * Delay requested by a `Retry-After` header: whole seconds or an HTTP date. A missing, malformed or
 * past value uses [fallbackMs]. The result is bounded to [maximumMs].
 */
internal fun retryAfterDelayMs(
    header: String?,
    nowMs: Long,
    fallbackMs: Long = DefaultCooldownMs,
    maximumMs: Long = MaximumCooldownMs,
): Long {
    val value = header?.trim().orEmpty()
    val seconds = value.toLongOrNull()?.takeIf { it >= 0 }
    val delay =
        when {
            seconds != null -> if (seconds > maximumMs / 1_000) maximumMs else seconds * 1_000
            value.isNotEmpty() ->
                httpDateMs(value)?.let { it - nowMs }?.takeIf { it > 0 } ?: fallbackMs
            else -> fallbackMs
        }
    return delay.coerceIn(0, maximumMs)
}

private fun httpDateMs(value: String): Long? =
    try {
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).parse(value)?.time
    } catch (_: java.text.ParseException) {
        null
    }

/**
 * One cooldown shared by every TMDB request. After a 429, all scan workers wait for the same window
 * instead of retrying on their own schedules and tripping the limit again.
 */
class TmdbRateLimiter(private val clock: () -> Long = System::currentTimeMillis) {
    private val cooldownUntil = AtomicLong(0)

    fun record(retryAfter: String?) {
        val now = clock()
        val candidate = now + retryAfterDelayMs(retryAfter, now)
        while (true) {
            val current = cooldownUntil.get()
            if (candidate <= current || cooldownUntil.compareAndSet(current, candidate)) return
        }
    }

    fun remainingMs(): Long = (cooldownUntil.get() - clock()).coerceAtLeast(0)
}

/** Applies [TmdbRateLimiter] to TMDB API calls only; image and other hosts pass through. */
class TmdbRateLimitInterceptor(private val limiter: TmdbRateLimiter) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.host != TMDB_API_HOST) return chain.proceed(request)
        // OkHttp threads are not coroutine-aware; wait in slices so a cancelled call stops waiting.
        while (true) {
            val remaining = limiter.remainingMs()
            if (remaining <= 0 || chain.call().isCanceled()) break
            try {
                Thread.sleep(minOf(remaining, WaitSliceMs))
            } catch (interrupted: InterruptedException) {
                Thread.currentThread().interrupt()
                throw java.io.InterruptedIOException("Interrupted during TMDB cooldown")
            }
        }
        val response = chain.proceed(request)
        if (response.code == 429) limiter.record(response.header("Retry-After"))
        return response
    }

    private companion object {
        const val TMDB_API_HOST = "api.themoviedb.org"
    }
}
