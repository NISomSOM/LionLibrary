package com.singam.lionlibrary.domain.usecase

import com.singam.lionlibrary.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

/** The injected scope outlives any player; each caller receives its own completion. */
class OrderedProgressWriter(
    scope: CoroutineScope,
    private val persist: suspend (PlaybackSnapshot) -> OperationResult<Unit, DataError>,
) {
    private data class Write(
        val snapshot: PlaybackSnapshot,
        val result: CompletableDeferred<OperationResult<Unit, DataError>>,
    )

    private val queue = Channel<Write>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (write in queue) {
                try {
                    write.result.complete(persist(write.snapshot))
                } catch (cancelled: CancellationException) {
                    write.result.cancel(cancelled)
                    throw cancelled
                } catch (_: Exception) {
                    write.result.complete(OperationResult.Failure(DataError.STORAGE))
                }
            }
        }
    }

    fun submit(snapshot: PlaybackSnapshot): Deferred<OperationResult<Unit, DataError>> {
        val result = CompletableDeferred<OperationResult<Unit, DataError>>()
        if (queue.trySend(Write(snapshot, result)).isFailure)
            result.complete(OperationResult.Failure(DataError.STORAGE))
        return result
    }
}
