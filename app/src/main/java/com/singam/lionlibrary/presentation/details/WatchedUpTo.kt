package com.singam.lionlibrary.presentation.details

import com.singam.lionlibrary.domain.model.Episode

/** Every earlier season, plus the target's season through its episode number, in list order. */
internal fun List<Episode>.idsWatchedUpTo(target: Episode): List<Long> {
    val order = compareBy<Episode>({ it.seasonNumber }, { it.episodeNumber })
    return filter { order.compare(it, target) <= 0 }.map { it.id }
}
