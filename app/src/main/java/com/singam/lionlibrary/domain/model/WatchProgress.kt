package com.singam.lionlibrary.domain.model

data class WatchProgress(
    val mediaId: Long,
    val episodeId: Long,
    val progress: Float,
    val lastPositionMs: Long,
    val durationMs: Long,
    val lastWatched: Long,
    val completed: Boolean
)

