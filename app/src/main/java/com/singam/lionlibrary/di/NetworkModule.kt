package com.singam.lionlibrary.di

import android.util.Log
import com.singam.lionlibrary.BuildConfig
import com.singam.lionlibrary.data.remote.TmdbRateLimitInterceptor
import com.singam.lionlibrary.data.remote.TmdbRateLimiter
import com.singam.lionlibrary.data.remote.UserAgentInterceptor
import com.singam.lionlibrary.data.remote.api.SkipDbApiService
import com.singam.lionlibrary.data.remote.api.TheIntroDbApiService
import com.singam.lionlibrary.data.remote.api.TmdbApiService
import com.singam.lionlibrary.util.Constants
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

// Declared before the module: top-level vals initialise in file order.
private val SkipTimesClient = named("skipTimesClient")
private val TheIntroDbRetrofit = named("theIntroDbRetrofit")
private val SkipDbRetrofit = named("skipDbRetrofit")

val networkModule = module {
    single { TmdbRateLimiter() }
    singleOf(::TmdbRateLimitInterceptor)

    single {
        val logging = HttpLoggingInterceptor { message ->
            // Hide API key in logs.
            val redacted = message.replace(Regex("api_key=[^&\\s]+"), "api_key=***")
            Log.d("OkHttp", redacted)
        }
            .apply {
                level =
                    if (BuildConfig.DEBUG) {
                        HttpLoggingInterceptor.Level.BASIC
                    } else {
                        HttpLoggingInterceptor.Level.NONE
                    }
            }
        OkHttpClient.Builder()
            .addInterceptor(get<TmdbRateLimitInterceptor>())
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    single {
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }
    }

    single {
        val json: Json = get()
        val contentType = "application/json".toMediaType()
        Retrofit.Builder()
            .baseUrl(Constants.TMDB_BASE_URL)
            .client(get())
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    single<TmdbApiService> {
        val retrofit: Retrofit = get()
        retrofit.create(TmdbApiService::class.java)
    }

    // Skip-time providers share the connection pool but not the TMDB client's long timeouts.
    single(SkipTimesClient) {
        skipTimesClient(
            get(),
            "LionLibrary/${BuildConfig.VERSION_NAME} Android",
        )
    }

    single(TheIntroDbRetrofit) {
        skipTimesRetrofit(Constants.THEINTRODB_BASE_URL, get(SkipTimesClient), get())
    }
    single(SkipDbRetrofit) {
        skipTimesRetrofit(Constants.SKIPDB_BASE_URL, get(SkipTimesClient), get())
    }
    single<TheIntroDbApiService> {
        get<Retrofit>(TheIntroDbRetrofit).create(TheIntroDbApiService::class.java)
    }
    single<SkipDbApiService> { get<Retrofit>(SkipDbRetrofit).create(SkipDbApiService::class.java) }
}

/** Short call timeout, no disk cache and an identifying User-Agent for keyless providers. */
internal fun skipTimesClient(
    base: OkHttpClient,
    userAgent: String,
    callTimeoutMs: Long = SKIP_TIMES_CALL_TIMEOUT_MS,
): OkHttpClient =
    base
        .newBuilder()
        .callTimeout(callTimeoutMs, TimeUnit.MILLISECONDS)
        .cache(null)
        .addInterceptor(UserAgentInterceptor(userAgent))
        .build()

internal fun skipTimesRetrofit(baseUrl: String, client: OkHttpClient, json: Json): Retrofit =
    Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

private const val SKIP_TIMES_CALL_TIMEOUT_MS = 8_000L
