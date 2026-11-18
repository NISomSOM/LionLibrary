package com.singam.lionlibrary.presentation.navigation

import com.singam.lionlibrary.domain.model.MediaType
import kotlinx.serialization.Serializable

@Serializable data object HomeRoute

/**
 * [filter] is a [com.singam.lionlibrary.domain.model.MediaFilter] name; [genres] preselect chips.
 */
@Serializable
data class LibraryRoute(val filter: String? = null, val genres: List<String> = emptyList())

@Serializable data object SettingsRoute

/**
 * [origin] and [posterPath] are set when a poster card opened the page: the card's shelf or grid
 * and its local poster file, so the page can share that poster with the card from its first frame.
 */
@Serializable
data class DetailsRoute(
    val mediaId: Long,
    val origin: String? = null,
    val posterPath: String? = null,
)

/** [mediaId] is a media row id for MOVIE and an episode row id otherwise. */
@Serializable data class PlayerRoute(val mediaType: MediaType, val mediaId: Long)
