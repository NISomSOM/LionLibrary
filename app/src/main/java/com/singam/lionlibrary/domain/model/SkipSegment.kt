package com.singam.lionlibrary.domain.model

/**
 * A chapter reported by the playing file; [endMs] is null when the container gives only a start.
 */
data class PlaybackChapter(val startMs: Long, val endMs: Long?, val title: String?)

enum class SkipSegmentType {
    INTRO,
    CREDITS,
}

/** Where a segment came from, in priority order: file chapters win over online providers. */
enum class SkipSource {
    CHAPTER,
    THEINTRODB,
    SKIPDB,
}

/**
 * A skippable part of the current file. [endMs] is null for credits that run to the end of the
 * file. [confidence] is the provider's 0–1 agreement score; chapters carry none.
 */
data class SkipSegment(
    val type: SkipSegmentType,
    val startMs: Long,
    val endMs: Long?,
    val source: SkipSource,
    val confidence: Double? = null,
)

/**
 * Online identity of one playable file. Local IDs stay the playback/history identity; this only
 * addresses the providers. [season]/[episode] are null for a movie.
 */
data class SkipLookupKey(
    val tmdbId: Int,
    val isMovie: Boolean,
    val season: Int? = null,
    val episode: Int? = null,
)

/**
 * Online lookups need a positive TMDB id; episodes also need their numbers. Combined-episode files
 * span several online records, so they use chapters only, like unidentified files.
 */
fun PlaybackTarget.skipLookupKey(): SkipLookupKey? {
    val id = tmdbId?.takeIf { it > 0 } ?: return null
    if (episodeId == 0L) return SkipLookupKey(id, isMovie = true)
    if (episodeEnd != null) return null
    val seasonNumber = season?.takeIf { it >= 0 } ?: return null
    val episodeNumber = episode?.takeIf { it >= 0 } ?: return null
    return SkipLookupKey(id, isMovie = false, season = seasonNumber, episode = episodeNumber)
}
