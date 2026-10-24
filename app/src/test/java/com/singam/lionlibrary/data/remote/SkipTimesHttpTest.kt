package com.singam.lionlibrary.data.remote

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.*
import com.singam.lionlibrary.data.remote.api.SkipDbApiService
import com.singam.lionlibrary.data.remote.api.TheIntroDbApiService
import com.singam.lionlibrary.data.remote.api.TmdbApiService
import com.singam.lionlibrary.di.skipTimesClient
import com.singam.lionlibrary.di.skipTimesRetrofit
import com.singam.lionlibrary.domain.model.*
import com.singam.lionlibrary.domain.repository.SettingsRepository
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Provider contracts over a local loopback server only (no internet). Break vectors: exact query
 * names and units per provider, movies sending no season/episode, the identifying User-Agent,
 * 404/empty bodies, malformed JSON, 429 with Retry-After (and failing fast during the cooldown),
 * one retry for 5xx, the call timeout, caller cancellation, and the TMDB key gate for IMDb ids.
 */
class SkipTimesHttpTest {
    private val server = MockWebServer()
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }
    private val hour = 3_600_000L
    private val episode = SkipLookupKey(1396, false, 1, 1)
    private var now = 1_000_000L
    private val gate = ProviderRequestGate(minIntervalMs = 0, clock = { now })

    @BeforeEach fun start() = server.start()

    @AfterEach fun stop() = server.close()

    private fun client(timeoutMs: Long = 8_000) =
        skipTimesClient(OkHttpClient(), "LionLibrary/2.1.0 Android", timeoutMs)

    private fun theIntroDb(timeoutMs: Long = 8_000) =
        RetrofitTheIntroDbDataSource(
            skipTimesRetrofit(server.url("/v4/").toString(), client(timeoutMs), json)
                .create(TheIntroDbApiService::class.java),
            gate,
        )

    private fun skipDb() =
        RetrofitSkipDbDataSource(
            skipTimesRetrofit(server.url("/").toString(), client(), json)
                .create(SkipDbApiService::class.java),
            gate,
        )

    private fun respond(code: Int = 200, body: String = "", header: Pair<String, String>? = null) =
        server.enqueue(
            MockResponse.Builder()
                .code(code)
                .body(body)
                .apply { header?.let { addHeader(it.first, it.second) } }
                .build()
        )

    @Test
    fun `TheIntroDB is asked with v4 names, milliseconds and details`() = runBlocking {
        respond(
            body =
                """{"tvdb_id":81189,"type":"tv","group_duration_ms":$hour,
                "intro":[{"start_ms":null,"end_ms":61000,"confidence":0.8,"submission_count":4}],
                "recap":[{"start_ms":0,"end_ms":20000,"confidence":0.9,"submission_count":4}]}"""
        )
        val result = theIntroDb().segments(episode, hour)
        val request = server.takeRequest()
        assertThat(request.url.encodedPath).isEqualTo("/v4/media")
        assertThat(request.url.queryParameter("tmdb_id")).isEqualTo("1396")
        assertThat(request.url.queryParameter("season")).isEqualTo("1")
        assertThat(request.url.queryParameter("episode")).isEqualTo("1")
        assertThat(request.url.queryParameter("duration_ms")).isEqualTo("3600000")
        assertThat(request.url.queryParameter("details")).isEqualTo("true")
        assertThat(request.headers["User-Agent"]).isEqualTo("LionLibrary/2.1.0 Android")
        assertThat(result)
            .isEqualTo(
                OperationResult.Success(
                    listOf(
                        SkipSegment(SkipSegmentType.INTRO, 0, 61_000, SkipSource.THEINTRODB, 0.8)
                    )
                )
            )
    }

    @Test
    fun `movies send neither season nor episode`() = runBlocking {
        respond(code = 404, body = """{"error":"no submissions","code":"no_submissions"}""")
        theIntroDb().segments(SkipLookupKey(603, isMovie = true), hour)
        val url = server.takeRequest().url
        assertThat(url.queryParameterNames).isEqualTo(setOf("tmdb_id", "duration_ms", "details"))
    }

    @Test
    fun `a 404 is no data rather than an error`() = runBlocking {
        respond(code = 404, body = """{"error":"media not found","code":"media_not_found"}""")
        assertThat(theIntroDb().segments(episode, hour))
            .isEqualTo(OperationResult.Success(emptyList()))
    }

    @Test
    fun `an episode-list fallback body has no segments`() = runBlocking {
        respond(body = """{"tvdb_id":1,"type":"tv","episodes":[{"season":1,"episode":1}]}""")
        assertThat(theIntroDb().segments(episode, hour))
            .isEqualTo(OperationResult.Success(emptyList()))
    }

    @Test
    fun `malformed JSON is a typed failure, not a crash`() = runBlocking {
        respond(body = """{"intro":[{"start_ms":""")
        assertThat(theIntroDb().segments(episode, hour))
            .isEqualTo(OperationResult.Failure(DataError.UNKNOWN))
    }

    @Test
    fun `429 starts a cooldown that fails fast without another request`() = runBlocking {
        respond(
            code = 429,
            body = """{"error":"Too Many Requests"}""",
            header = "Retry-After" to "30",
        )
        val source = theIntroDb()
        assertThat(source.segments(episode, hour))
            .isEqualTo(OperationResult.Failure(DataError.SERVER))
        assertThat(source.segments(episode, hour))
            .isEqualTo(OperationResult.Failure(DataError.SERVER))
        assertThat(server.requestCount).isEqualTo(1)
        assertThat(gate.cooldownRemainingMs()).isEqualTo(30_000L)
        now += 30_000
        respond(code = 404)
        assertThat(source.segments(episode, hour)).isEqualTo(OperationResult.Success(emptyList()))
    }

    @Test
    fun `TheIntroDB reset seconds are honoured when Retry-After is absent`() = runBlocking {
        respond(code = 429, header = "X-RateLimit-Reset" to "7")
        theIntroDb().segments(episode, hour)
        assertThat(gate.cooldownRemainingMs()).isEqualTo(7_000L)
    }

    @Test
    fun `one transient server error is retried once`() = runBlocking {
        respond(code = 503)
        respond(code = 404)
        assertThat(theIntroDb().segments(episode, hour))
            .isEqualTo(OperationResult.Success(emptyList()))
        assertThat(server.requestCount).isEqualTo(2)
    }

    @Test
    fun `persistent server errors give up after the single retry`() = runBlocking {
        repeat(3) { respond(code = 500) }
        assertThat(theIntroDb().segments(episode, hour))
            .isEqualTo(OperationResult.Failure(DataError.SERVER))
        assertThat(server.requestCount).isEqualTo(2)
    }

    @Test
    fun `the call timeout fails the lookup instead of hanging`() = runBlocking {
        repeat(2) {
            server.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .body("{}")
                    .headersDelay(2, TimeUnit.SECONDS)
                    .build()
            )
        }
        assertThat(theIntroDb(timeoutMs = 200).segments(episode, hour))
            .isEqualTo(OperationResult.Failure(DataError.SERVER))
    }

    @Test
    fun `cancelling the caller propagates instead of becoming a result`() {
        server.enqueue(
            MockResponse.Builder().code(200).body("{}").headersDelay(5, TimeUnit.SECONDS).build()
        )
        assertFailure { runBlocking { withTimeout(300) { theIntroDb().segments(episode, hour) } } }
            .isInstanceOf(TimeoutCancellationException::class)
    }

    @Test
    fun `SkipDB is asked by IMDb id with whole seconds and no shifting`() = runBlocking {
        respond(
            body =
                """{"imdb_id":"tt0903747","season":1,"episode":1,"segments":{"intro":null,
                "recap":{"start_ms":0,"end_ms":30000,"match":"exact","adjusted":false,"confidence":1},
                "outro":{"start_ms":3500000,"end_ms":3599000,"adjusted":false,"offset_ms":0,
                "match":"exact","confidence":0.7},"preview":null},"intro_length_estimate_ms":40000}"""
        )
        val result = skipDb().segments("tt0903747", episode, hour + 499)
        val url = server.takeRequest().url
        assertThat(url.encodedPath).isEqualTo("/api/segments")
        assertThat(url.queryParameter("imdb_id")).isEqualTo("tt0903747")
        assertThat(url.queryParameter("season")).isEqualTo("1")
        assertThat(url.queryParameter("episode")).isEqualTo("1")
        assertThat(url.queryParameter("duration")).isEqualTo("3600")
        assertThat(url.queryParameter("adjust")).isEqualTo("none")
        assertThat(result)
            .isEqualTo(
                OperationResult.Success(
                    listOf(
                        SkipSegment(
                            SkipSegmentType.CREDITS,
                            3_500_000,
                            3_599_000,
                            SkipSource.SKIPDB,
                            0.7,
                        )
                    )
                )
            )
    }

    @Test
    fun `SkipDB null segments are an empty answer`() = runBlocking {
        respond(body = """{"segments":{"intro":null,"recap":null,"outro":null,"preview":null}}""")
        assertThat(skipDb().segments("tt0903747", episode, hour))
            .isEqualTo(OperationResult.Success(emptyList()))
    }

    private fun imdbSource(key: String) =
        TmdbImdbIdDataSource(
            skipTimesRetrofit(server.url("/3/").toString(), OkHttpClient(), json)
                .create(TmdbApiService::class.java),
            SettingsWithKey(key),
        )

    @Test
    fun `episodes look up the show-level IMDb id with the user's key`() = runBlocking {
        respond(body = """{"id":1396,"imdb_id":"tt0903747","tvdb_id":81189}""")
        assertThat(imdbSource("key123").imdbId(episode))
            .isEqualTo(OperationResult.Success("tt0903747"))
        val url = server.takeRequest().url
        assertThat(url.encodedPath).isEqualTo("/3/tv/1396/external_ids")
        assertThat(url.queryParameter("api_key")).isEqualTo("key123")
    }

    @Test
    fun `movies use their own external ids and malformed ids are dropped`() = runBlocking {
        respond(body = """{"id":603,"imdb_id":"nm0000206"}""")
        assertThat(imdbSource("key123").imdbId(SkipLookupKey(603, isMovie = true)))
            .isEqualTo(OperationResult.Success(null))
        assertThat(server.takeRequest().url.encodedPath).isEqualTo("/3/movie/603/external_ids")
    }

    @Test
    fun `without a TMDB key no request is made`() = runBlocking {
        assertThat(imdbSource("  ").imdbId(episode)).isEqualTo(OperationResult.Success(null))
        assertThat(server.requestCount).isEqualTo(0)
    }

    private class SettingsWithKey(key: String) : SettingsRepository {
        override val keyVerified: Flow<Boolean> = flowOf(true)
        override val setupDismissed: Flow<Boolean> = flowOf(true)
        override val scanSummary: Flow<ScanSummary> = flowOf(ScanSummary())
        override val tmdbApiKey: Flow<String> = flowOf(key)
        override val moviesFolderUri: Flow<String> = flowOf("")
        override val showsFolderUri: Flow<String> = flowOf("")
        override val lastScanTime: Flow<Long> = flowOf(0)
        override val forceLibVlc: Flow<Boolean> = flowOf(false)
        override val forceExternalPlayer: Flow<Boolean> = flowOf(false)
        override val onlineSkipTimes: Flow<Boolean> = flowOf(true)

        override suspend fun setKeyVerified(verified: Boolean) {}

        override suspend fun setSetupDismissed(dismissed: Boolean) {}

        override suspend fun setScanSummary(summary: ScanSummary) {}

        override suspend fun setTmdbApiKey(key: String) {}

        override suspend fun setMoviesFolderUri(uri: String) {}

        override suspend fun setShowsFolderUri(uri: String) {}

        override suspend fun setLastScanTime(time: Long) {}

        override suspend fun setForceLibVlc(enabled: Boolean) {}

        override suspend fun setForceExternalPlayer(enabled: Boolean) {}

        override suspend fun setOnlineSkipTimes(enabled: Boolean) {}
    }
}
