package com.example.mediahub.domain.model

data class MediaWithProgress(
    val mediaItem: MediaItem,
    val progress: Float,
    val lastWatched: Long
)
