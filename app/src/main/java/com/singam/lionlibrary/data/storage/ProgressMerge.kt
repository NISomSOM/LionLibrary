package com.singam.lionlibrary.data.storage

import com.singam.lionlibrary.data.local.db.entity.WatchProgressEntity

/**
 * Chooses which history survives when a duplicate row is folded into its episode. A completed
 * record wins; otherwise the more recently watched one wins, and ties keep the episode's own
 * record. The result always carries the episode's identity.
 */
internal fun mergeDuplicateProgress(
    episode: WatchProgressEntity?,
    duplicate: WatchProgressEntity,
    mediaId: Long,
    episodeId: Long,
): WatchProgressEntity {
    val winner =
        when {
            episode == null -> duplicate
            episode.completed != duplicate.completed ->
                if (duplicate.completed) duplicate else episode
            duplicate.lastWatched > episode.lastWatched -> duplicate
            else -> episode
        }
    return winner.copy(mediaId = mediaId, episodeId = episodeId)
}
