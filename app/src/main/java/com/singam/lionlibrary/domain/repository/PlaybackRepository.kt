package com.singam.lionlibrary.domain.repository

import com.singam.lionlibrary.domain.model.*
import kotlinx.coroutines.Deferred

interface PlaybackRepository {
    suspend fun target(type: MediaType, id: Long): PlaybackTarget?

    suspend fun adjacent(target: PlaybackTarget, next: Boolean): PlaybackTarget?

    suspend fun progress(target: PlaybackTarget): WatchProgress?

    /** App-lifetime FIFO: an exit write cannot overtake completion or an earlier episode. */
    fun save(snapshot: PlaybackSnapshot): Deferred<OperationResult<Unit, DataError>>
}
