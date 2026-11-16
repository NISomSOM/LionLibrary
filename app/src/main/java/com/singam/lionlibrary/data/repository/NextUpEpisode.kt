package com.singam.lionlibrary.data.repository

import com.singam.lionlibrary.data.local.db.entity.EpisodeEntity

/**
 * The first local episode after [season]/[lastEpisode] in season/episode order, matching
 * `EpisodeDao.getNextEpisode` and player auto-next. For a combined file, pass its end number as
 * [lastEpisode] so the next pick lies beyond the whole range. Ties fall back to the lower row id.
 */
internal fun nextEpisodeAfter(
    episodes: Iterable<EpisodeEntity>,
    season: Int,
    lastEpisode: Int,
): EpisodeEntity? =
    episodes
        .filter {
            it.seasonNumber > season ||
                (it.seasonNumber == season && it.episodeNumber > lastEpisode)
        }
        .minWithOrNull(compareBy({ it.seasonNumber }, { it.episodeNumber }, { it.id }))
