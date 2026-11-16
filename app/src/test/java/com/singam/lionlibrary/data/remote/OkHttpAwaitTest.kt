package com.singam.lionlibrary.data.remote

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Loopback only. Break vectors: non-2xx answers are responses (Letterboxd caches a 404 miss),
 * transport failure surfaces as IOException, and cancelling the caller cancels the HTTP call
 * instead of blocking the thread until the server answers.
 */
class OkHttpAwaitTest {
    private val server = MockWebServer()
    private val client = OkHttpClient()

    @BeforeEach fun start() = server.start()

    @AfterEach fun stop() = server.close()

    private fun call() = client.newCall(Request.Builder().url(server.url("/film/")).build())

    @Test
    fun `404 is returned as a response, not an exception`() = runBlocking {
        server.enqueue(MockResponse.Builder().code(404).body("missing").build())

        val code = call().await().use { it.code }

        assertThat(code).isEqualTo(404)
    }

    @Test
    fun `transport failure is an IOException`() {
        val dead = OkHttpClient.Builder().callTimeout(1, TimeUnit.SECONDS).build()
        val unreachable = server.url("/").also { server.close() }

        assertFailure {
                runBlocking { dead.newCall(Request.Builder().url(unreachable).build()).await() }
            }
            .isInstanceOf(IOException::class)
    }

    @Test
    fun `cancelling the caller cancels the call`() {
        server.enqueue(
            MockResponse.Builder().code(200).body("late").headersDelay(5, TimeUnit.SECONDS).build()
        )
        val call = call()

        assertFailure { runBlocking { withTimeout(200) { call.await() } } }
            .isInstanceOf(TimeoutCancellationException::class)
        assertThat(call.isCanceled()).isTrue()
    }
}
