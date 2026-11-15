package com.singam.lionlibrary.domain.model

data class HomeContent(
    val jumpBackInItems: List<JumpBackInItem>,
    val movies: List<MediaItem>,
    val tvShows: List<MediaItem>,
    val anime: List<MediaItem>,
    val recentlyAdded: List<MediaItem>,
    /** Every identified title once, by local id: movies, then TV, then anime series. */
    val allMedia: List<MediaItem> = emptyList(),
    /** Genre shelves sorted by name; a title appears once per genre. */
    val genres: Map<String, List<MediaItem>> = emptyMap(),
)
