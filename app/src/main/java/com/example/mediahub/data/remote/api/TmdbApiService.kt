package com.example.mediahub.data.remote.api

import com.example.mediahub.data.remote.dto.MovieDetailsDto
import com.example.mediahub.data.remote.dto.MovieSearchResponse
import com.example.mediahub.data.remote.dto.SeasonDetailsDto
import com.example.mediahub.data.remote.dto.TvDetailsDto
import com.example.mediahub.data.remote.dto.TvImagesResponseDto
import com.example.mediahub.data.remote.dto.TvSearchResponse
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

    @GET("movie/{id}")
    suspend fun getMovieDetails(
        @Path("id") id: Int,
        @Query("api_key") key: String
    ): MovieDetailsDto

    @GET("tv/{id}")
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
}
