package com.singam.lionlibrary.domain.model

enum class LibrarySort {
    TITLE_ASC,
    TITLE_DESC,
    RECENT,
    YEAR,
    RATING,
}

enum class WatchFilter {
    ALL,
    UNWATCHED,
    IN_PROGRESS,
    WATCHED,
}

data class LibraryQuery(
    val text: String = "",
    val type: MediaFilter = MediaFilter.ALL,
    val genre: String = "",
    val year: Int? = null,
    val watch: WatchFilter = WatchFilter.ALL,
    val sort: LibrarySort = LibrarySort.TITLE_ASC,
    val watchlistOnly: Boolean = false,
)
