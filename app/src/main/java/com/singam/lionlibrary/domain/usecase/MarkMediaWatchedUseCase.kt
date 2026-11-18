package com.singam.lionlibrary.domain.usecase

import com.singam.lionlibrary.domain.model.MediaItem
import com.singam.lionlibrary.domain.model.isSingleFile
import com.singam.lionlibrary.domain.repository.MediaRepository
import com.singam.lionlibrary.domain.repository.WatchProgressRepository
import kotlinx.coroutines.flow.first

/**
 * Marks a whole title as watched from a poster's long-press options: the single file of a movie or
 * local file (history episode id 0), or every local episode of a show.
 */
class MarkMediaWatchedUseCase(
    private val media: MediaRepository,
    private val progress: WatchProgressRepository,
) {
    suspend operator fun invoke(item: MediaItem) {
        if (item.isSingleFile) {
            progress.markAsWatched(item.id, MOVIE_EPISODE_ID)
        } else {
            val episodeIds = media.getAllEpisodesForShow(item.id).first().map { it.id }
            if (episodeIds.isNotEmpty()) progress.markAllAsWatched(item.id, episodeIds)
        }
    }

    private companion object {
        const val MOVIE_EPISODE_ID = 0L
    }
}
