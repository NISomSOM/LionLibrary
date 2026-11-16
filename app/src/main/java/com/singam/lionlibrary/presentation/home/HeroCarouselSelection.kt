package com.singam.lionlibrary.presentation.home

import com.singam.lionlibrary.domain.model.MediaItem
import kotlin.random.Random

internal const val HERO_CAROUSEL_SIZE = 10

/**
 * Keeps the titles already in the hero (same order, refreshed data) and tops the set up with random
 * titles when it is short, for example after the first emission of a first scan. Titles are unique
 * by local media id.
 */
internal fun selectHeroCarousel(
    previous: List<MediaItem>,
    available: List<MediaItem>,
    random: Random,
    size: Int = HERO_CAROUSEL_SIZE,
): List<MediaItem> {
    if (size <= 0) return emptyList()
    val byId = available.associateBy { it.id }
    val kept = previous.mapNotNull { byId[it.id] }.distinctBy { it.id }.take(size)
    if (kept.size == size) return kept
    val keptIds = kept.mapTo(HashSet()) { it.id }
    val additions = byId.values.filter { it.id !in keptIds }.shuffled(random).take(size - kept.size)
    return kept + additions
}
