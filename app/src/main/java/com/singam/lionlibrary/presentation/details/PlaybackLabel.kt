package com.singam.lionlibrary.presentation.details

import androidx.annotation.StringRes
import com.singam.lionlibrary.R
import com.singam.lionlibrary.domain.model.isSingleFile

@StringRes
internal fun DetailsState.playbackLabel(): Int =
    if (media?.isSingleFile == true) {
        when {
            isMovieWatched -> R.string.replay
            moviePositionMs > 0 -> R.string.resume
            else -> R.string.play
        }
    } else {
        when {
            nextEpisodeToWatch?.id in watchedEpisodeIds -> R.string.replay
            nextEpisodeToWatch?.id in resumeEpisodeIds -> R.string.resume
            watchedEpisodeIds.any { it > 0 } -> R.string.next_episode
            else -> R.string.play
        }
    }
