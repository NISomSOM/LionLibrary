package com.singam.lionlibrary.domain.usecase

import com.singam.lionlibrary.domain.model.MediaItem
import com.singam.lionlibrary.domain.model.genreList

/**
 * Other titles of the library that share at least one genre with [target]: most shared genres
 * first, then the same media type, then title. The target itself and duplicate ids are dropped.
 */
fun relatedMedia(target: MediaItem, candidates: List<MediaItem>): List<MediaItem> {
    val own = target.genreList.map { it.lowercase() }.toSet()
    if (own.isEmpty()) return emptyList()
    return candidates
        .asSequence()
        .filter { it.id != target.id }
        .distinctBy { it.id }
        .map { item -> item to item.genreList.count { it.lowercase() in own } }
        .filter { (_, shared) -> shared > 0 }
        .sortedWith(
            compareByDescending<Pair<MediaItem, Int>> { it.second }
                .thenBy { if (it.first.mediaType == target.mediaType) 0 else 1 }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.first.title }
                .thenBy { it.first.id }
        )
        .map { it.first }
        .toList()
}
