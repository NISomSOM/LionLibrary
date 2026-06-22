package com.singam.lionlibrary.domain.model

data class HomeContent(
    val jumpBackInItems: List<JumpBackInItem>,
    val movies: List<MediaItem>,
    val tvShows: List<MediaItem>,
    val anime: List<MediaItem>,
    val recentlyAdded: List<MediaItem>
)

