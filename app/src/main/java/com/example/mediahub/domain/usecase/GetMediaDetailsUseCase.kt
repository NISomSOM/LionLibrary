package com.example.mediahub.domain.usecase

import com.example.mediahub.domain.model.Episode
import com.example.mediahub.domain.model.MediaItem
import com.example.mediahub.domain.model.Season
import com.example.mediahub.domain.model.WatchProgress
import com.example.mediahub.domain.repository.MediaRepository
import com.example.mediahub.domain.repository.WatchProgressRepository
import kotlinx.coroutines.flow.Flow

data class MediaDetails(
    val media: MediaItem,
    val seasons: List<Season>,
    val episodes: List<Episode>
)

class GetMediaDetailsUseCase(
    private val mediaRepository: MediaRepository,
    private val watchProgressRepository: WatchProgressRepository
) {
    suspend operator fun invoke(mediaId: Long): MediaDetails? {
        val media = mediaRepository.getById(mediaId) ?: return null
        return MediaDetails(
            media = media,
            seasons = emptyList(),  // loaded on-demand per season
            episodes = emptyList() // loaded on-demand per season
        )
    }

    fun getProgress(mediaId: Long): Flow<List<WatchProgress>> {
        return watchProgressRepository.getProgressForMedia(mediaId)
    }

    fun getSeasons(showId: Long): Flow<List<Season>> {
        return mediaRepository.getSeasonsForShow(showId)
    }

    fun getEpisodesForSeason(showId: Long, seasonNumber: Int): Flow<List<Episode>> {
        return mediaRepository.getEpisodesForSeason(showId, seasonNumber)
    }

    fun getAllEpisodesForShow(showId: Long): Flow<List<Episode>> {
        return mediaRepository.getAllEpisodesForShow(showId)
    }
}
