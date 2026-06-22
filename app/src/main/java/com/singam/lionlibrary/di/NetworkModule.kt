package com.singam.lionlibrary.di

import com.singam.lionlibrary.data.remote.api.TmdbApiService
import com.singam.lionlibrary.util.Constants
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.dsl.module
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

val networkModule = module {

    single {
        val logging = HttpLoggingInterceptor { message ->
            // Redact API key from logged URLs
            val redacted = message.replace(Regex("api_key=[^&\\s]+"), "api_key=***")
            android.util.Log.d("OkHttp", redacted)
        }.apply {
            level = if (com.singam.lionlibrary.BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
        OkHttpClient.Builder()
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
}

