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
    /** Selected genres; a title matches when it has any of them. Empty means every genre. */
    val genres: Set<String> = emptySet(),
    val year: Int? = null,
    val watch: WatchFilter = WatchFilter.ALL,
    val sort: LibrarySort = LibrarySort.TITLE_ASC,
    val watchlistOnly: Boolean = false,
)
