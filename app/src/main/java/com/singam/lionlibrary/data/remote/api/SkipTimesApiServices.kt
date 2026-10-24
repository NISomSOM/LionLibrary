package com.singam.lionlibrary.data.remote.api

import com.singam.lionlibrary.data.remote.dto.SkipDbResponseDto
import com.singam.lionlibrary.data.remote.dto.TheIntroDbMediaDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * TheIntroDB v4, base `https://api.theintrodb.org/v4/`. Keyless reads; null queries are omitted.
 */
interface TheIntroDbApiService {
    @GET("media")
    suspend fun media(
        @Query("tmdb_id") tmdbId: Int,
        @Query("season") season: Int?,
        @Query("episode") episode: Int?,
        @Query("duration_ms") durationMs: Long,
        @Query("details") details: Boolean = true,
    ): Response<TheIntroDbMediaDto>
}

/** SkipDB, base `https://api.skipdb.tv/`. [durationSeconds] is the stream length in seconds. */
interface SkipDbApiService {
    @GET("api/segments")
    suspend fun segments(
        @Query("imdb_id") imdbId: String,
        @Query("season") season: Int?,
        @Query("episode") episode: Int?,
        @Query("duration") durationSeconds: Long,
        @Query("adjust") adjust: String = "none",
    ): Response<SkipDbResponseDto>
}
