package com.example.mediahub.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(
    tableName = "watch_progress",
    primaryKeys = ["mediaId", "episodeId"]
)
data class WatchProgressEntity(
    val mediaId: Long,
    val episodeId: Long,
    val progress: Float,
    val lastPositionMs: Long,
    val durationMs: Long,
    val lastWatched: Long,
    val completed: Boolean
)
