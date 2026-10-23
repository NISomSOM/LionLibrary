package com.singam.lionlibrary.domain.model
data class PlaybackTarget(
    val mediaId: Long,
    val episodeId: Long,
    val type: MediaType,
    val title: String,
    val filePath: String?,
    val subtitlePath: String?,
    val preferredEngine: String,
    val year: Int? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val episodeEnd: Int? = null,
    val episodeTitle: String? = null,
    val preferJapaneseAudio: Boolean = type == MediaType.ANIME,
    /** Local title logo of the movie or show, for the paused overlay. */
    val logoPath: String? = null,
    /** Episode synopsis, falling back to the show's; the movie's for films. */
    val overview: String? = null,
)

/** Captured before any suspension; never looks up a mutable current episode when saving. */
data class PlaybackSnapshot(
    val mediaId: Long,
    val episodeId: Long,
    val positionMs: Long,
    val durationMs: Long,
    val ended: Boolean = false,
    val recordedAt: Long,
) {
    fun toProgress(): WatchProgress {
        val duration = durationMs.coerceAtLeast(0)
        val position =
            positionMs.coerceAtLeast(0).let { if (duration > 0) it.coerceAtMost(duration) else it }
        val complete = ended || (duration > 0 && position.toDouble() / duration >= 0.95)
        val ratio =
            if (complete) 1f
            else if (duration > 0) (position.toDouble() / duration).toFloat().coerceIn(0f, 1f)
            else 0f
        return WatchProgress(
            mediaId,
            episodeId,
            ratio,
            if (ended) 0 else position,
            duration,
            recordedAt,
            complete,
        )
    }
}
