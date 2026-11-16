package com.singam.lionlibrary.presentation.navigation

import com.singam.lionlibrary.domain.model.MediaType
import kotlinx.serialization.Serializable

@Serializable data object HomeRoute

@Serializable data class LibraryRoute(val filter: String? = null)

@Serializable data object SettingsRoute

@Serializable data class DetailsRoute(val mediaId: Long)

/** [mediaId] is a media row id for MOVIE and an episode row id otherwise. */
@Serializable data class PlayerRoute(val mediaType: MediaType, val mediaId: Long)
