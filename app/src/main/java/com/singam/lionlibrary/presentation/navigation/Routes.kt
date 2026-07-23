package com.singam.lionlibrary.presentation.navigation

object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val SEARCH_ROUTE = "search?filter={filter}"
    const val SETTINGS = "settings"
    const val MOVIE_DETAILS = "movie_details/{mediaId}"
    const val SHOW_DETAILS = "show_details/{mediaId}"
    const val EPISODE_DETAILS = "episode_details/{episodeId}"
    const val PLAYER = "player/{mediaType}/{mediaId}"

    fun searchWithFilter(filter: String) = "search?filter=$filter"
    fun movieDetails(mediaId: Long) = "movie_details/$mediaId"
    fun showDetails(mediaId: Long) = "show_details/$mediaId"
    fun episodeDetails(episodeId: Long) = "episode_details/$episodeId"
    fun player(mediaType: String, mediaId: Long) = "player/$mediaType/$mediaId"
}

