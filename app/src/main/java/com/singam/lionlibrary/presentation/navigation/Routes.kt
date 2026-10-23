package com.singam.lionlibrary.presentation.navigation

import kotlinx.serialization.Serializable

@Serializable data object HomeRoute

@Serializable data class LibraryRoute(val filter: String? = null)

@Serializable data object SettingsRoute

@Serializable data class DetailsRoute(val mediaId: Long)

@Serializable data class PlayerRoute(val mediaType: String, val mediaId: Long)
