package com.example.mediahub.presentation.navigation

object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val MOVIE_DETAILS = "movie_details/{mediaId}"
    const val SHOW_DETAILS = "show_details/{mediaId}"
    const val EPISODE_DETAILS = "episode_details/{episodeId}"

    fun movieDetails(mediaId: Long) = "movie_details/$mediaId"
    fun showDetails(mediaId: Long) = "show_details/$mediaId"
    fun episodeDetails(episodeId: Long) = "episode_details/$episodeId"
}
