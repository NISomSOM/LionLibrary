package com.singam.lionlibrary.data.scanner

import com.singam.lionlibrary.data.local.db.entity.EpisodeEntity
import com.singam.lionlibrary.data.local.db.entity.MediaEntity

/**
 * What the library held when a scan started (after missing-file reconciliation), read once so the
 * per-file "is this known?" checks and sidecar/range back-fills need no query each. Every file
 * appears in at most one candidate of a scan, so rows this scan writes never need to be looked up
 * here. Paths that fail or turn out unidentified still go to the database for fresh state.
 */
internal class ScanIndex(media: List<MediaEntity>, episodes: List<EpisodeEntity>) {
    private val mediaByPath: Map<String, MediaEntity> =
        media.mapNotNull { row -> row.filePath?.let { it to row } }.toMap()
    private val episodesByPath: Map<String, EpisodeEntity> = episodes.associateBy { it.filePath }

    fun media(path: String): MediaEntity? = mediaByPath[path]

    fun isKnownEpisode(path: String): Boolean = path in episodesByPath

    /**
     * Back-fills an episode row would get from `updateRange` / `fillMissingSubtitle`: only when the
     * row exists, its field is still empty and the scan found a value. Existing values never
     * change.
     */
    fun episodeBackfill(path: String, rangeEnd: Int?, subtitle: String?): EpisodeBackfill? {
        val known = episodesByPath[path] ?: return null
        val range = rangeEnd.takeIf { known.episodeEndNumber == null }
        val sidecar = subtitle.takeIf { known.externalSubtitlePath == null }
        return if (range == null && sidecar == null) null else EpisodeBackfill(range, sidecar)
    }

    /** Subtitle for a known media row whose sidecar is still empty; null when nothing changes. */
    fun mediaSubtitleBackfill(path: String, subtitle: String?): String? {
        val known = mediaByPath[path] ?: return null
        return subtitle.takeIf { known.externalSubtitlePath == null }
    }
}

internal data class EpisodeBackfill(val rangeEnd: Int?, val subtitle: String?)
