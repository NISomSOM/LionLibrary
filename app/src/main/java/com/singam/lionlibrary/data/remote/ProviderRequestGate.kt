package com.singam.lionlibrary.data.remote

import com.singam.lionlibrary.domain.model.DataError
import com.singam.lionlibrary.domain.model.OperationResult
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import okhttp3.Headers
import retrofit2.Response

private const val ProviderMinimumCooldownMs = 2_000L
private const val ProviderMaximumCooldownMs = 600_000L
private const val ProviderRetryDelayMs = 1_000L

/**
 * Request spacing and 429 cooldown for one skip-time provider. Unlike the shared TMDB limiter this
 * never waits out a cooldown: skip buttons are only useful now, so a cooled-down provider fails
 * fast and the next file asks again.
 */
class ProviderRequestGate(
    private val minIntervalMs: Long,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val spacing = Mutex()
    private var nextAllowedAt = 0L
    private val cooldownUntil = AtomicLong(0)

    fun cooldownRemainingMs(): Long = (cooldownUntil.get() - clock()).coerceAtLeast(0)

    /** `Retry-After` wins; TheIntroDB sends `X-RateLimit-Reset`/`X-UsageLimit-Reset` seconds. */
    fun recordRateLimit(headers: Headers) {
        val now = clock()
        val header =
            headers["Retry-After"] ?: headers["X-RateLimit-Reset"] ?: headers["X-UsageLimit-Reset"]
        val delay =
            retryAfterDelayMs(header, now, maximumMs = ProviderMaximumCooldownMs)
                .coerceAtLeast(ProviderMinimumCooldownMs)
        val candidate = now + delay
        while (true) {
            val current = cooldownUntil.get()
            if (candidate <= current || cooldownUntil.compareAndSet(current, candidate)) return
        }
    }

    /** Waits for this provider's request spacing, then runs [block]. */
    suspend fun <T> spaced(block: suspend () -> T): T {
        spacing.withLock {
            val wait = nextAllowedAt - clock()
            if (wait > 0) delay(wait)
            nextAllowedAt = clock() + minIntervalMs
        }
        return block()
    }
}

/**
 * Calls a provider endpoint and maps the outcome. 404 means "no data" (success with null). One
 * retry covers a transient I/O failure or 5xx; 429 records the cooldown and is not retried.
 */
internal suspend fun <T : Any> ProviderRequestGate.call(
    request: suspend () -> Response<T>
): OperationResult<T?, DataError> {
    var attempt = 0
    while (true) {
        if (cooldownRemainingMs() > 0) return OperationResult.Failure(DataError.SERVER)
        val retryable: DataError =
            try {
                val response = spaced { request() }
                when (response.code()) {
                    in 200..299 -> return OperationResult.Success(response.body())
                    404 -> return OperationResult.Success(null)
                    429 -> {
                        recordRateLimit(response.headers())
                        return OperationResult.Failure(DataError.SERVER)
                    }
                    401,
                    403 -> return OperationResult.Failure(DataError.UNAUTHORIZED)
                    in 500..599 -> DataError.SERVER
                    else -> return OperationResult.Failure(DataError.UNKNOWN)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: SerializationException) {
                return OperationResult.Failure(DataError.UNKNOWN)
            } catch (_: InterruptedIOException) {
                DataError.SERVER // Includes the call timeout.
            } catch (_: IOException) {
                DataError.OFFLINE
            }
        if (attempt++ >= 1) return OperationResult.Failure(retryable)
        delay(ProviderRetryDelayMs)
    }
}
