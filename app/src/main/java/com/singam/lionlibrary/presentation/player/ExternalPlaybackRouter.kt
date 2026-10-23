package com.singam.lionlibrary.presentation.player

import android.content.Intent
import android.net.Uri
import com.singam.lionlibrary.domain.model.MediaType
import com.singam.lionlibrary.domain.repository.MediaRepository
import com.singam.lionlibrary.domain.repository.SettingsRepository
import com.singam.lionlibrary.domain.repository.WatchProgressRepository
import com.singam.lionlibrary.domain.usecase.LaunchPlayerUseCase
import kotlinx.coroutines.flow.first

// Decides whether a Play action goes to an external app instead of the in-app player.
class ExternalPlaybackRouter(
    private val settingsRepository: SettingsRepository,
    private val mediaRepository: MediaRepository,
    private val watchProgressRepository: WatchProgressRepository,
    private val launchPlayerUseCase: LaunchPlayerUseCase,
) {

    // Null when the toggle is off or the file can't be resolved, so callers fall through to the
    // in-app player.
    // targetId is the media id for movies, the episode id otherwise (same as the player route).
    suspend fun intentIfForced(
        mediaType: MediaType,
        targetId: Long,
        fromStart: Boolean = false,
    ): Intent? {
        if (!settingsRepository.forceExternalPlayer.first()) return null

        return try {
            val (path, progressKey) =
                if (mediaType == MediaType.MOVIE) {
                    val media = mediaRepository.getById(targetId) ?: return null
                    media.filePath to (targetId to 0L)
                } else {
                    val episode = mediaRepository.getEpisodeById(targetId) ?: return null
                    episode.filePath to (episode.showId to episode.id)
                }
            if (path == null) return null

            val progress =
                watchProgressRepository.getProgress(progressKey.first, progressKey.second)
            val position =
                if (fromStart || progress == null || progress.completed) 0L
                else progress.lastPositionMs
            launchPlayerUseCase(Uri.parse(path), position)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            null
        }
    }
}
