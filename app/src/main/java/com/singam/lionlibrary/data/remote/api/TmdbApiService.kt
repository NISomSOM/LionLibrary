package com.singam.lionlibrary.data.remote.api

import com.singam.lionlibrary.data.remote.dto.MovieDetailsDto
import com.singam.lionlibrary.data.remote.dto.MovieSearchResponse
import com.singam.lionlibrary.data.remote.dto.SeasonDetailsDto
import com.singam.lionlibrary.data.remote.dto.TvDetailsDto
import com.singam.lionlibrary.data.remote.dto.TvImagesResponseDto
import com.singam.lionlibrary.data.remote.dto.TvSearchResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface TmdbApiService {

    @GET("search/movie")
    suspend fun searchMovie(
        @Query("api_key") key: String,
        @Query("query") query: String,
        @Query("year") year: Int? = null
    ): MovieSearchResponse

    @GET("search/tv")
    suspend fun searchTv(
        @Query("api_key") key: String,
        @Query("query") query: String
    ): TvSearchResponse

    @GET("movie/{id}?append_to_response=release_dates")
    suspend fun getMovieDetails(
        @Path("id") id: Int,
        @Query("api_key") key: String
    ): MovieDetailsDto

    @GET("tv/{id}?append_to_response=content_ratings")
    suspend fun getTvDetails(
        @Path("id") id: Int,
        @Query("api_key") key: String
    ): TvDetailsDto

    @GET("tv/{id}/season/{season}")
    suspend fun getSeasonDetails(
        @Path("id") id: Int,
        @Path("season") season: Int,
        @Query("api_key") key: String
    ): SeasonDetailsDto

    @GET("tv/{id}/images")
    suspend fun getTvImages(
        @Path("id") id: Int,
        @Query("api_key") key: String
    ): TvImagesResponseDto

    @GET("movie/{id}/images")
    suspend fun getMovieImages(
        @Path("id") id: Int,
        @Query("api_key") key: String
    ): TvImagesResponseDto
}

