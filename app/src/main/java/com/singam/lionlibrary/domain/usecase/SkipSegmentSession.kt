package com.singam.lionlibrary.domain.usecase

import com.singam.lionlibrary.domain.model.OperationResult
import com.singam.lionlibrary.domain.model.PlaybackChapter
import com.singam.lionlibrary.domain.model.SkipLookupKey
import com.singam.lionlibrary.domain.model.SkipSegment
import com.singam.lionlibrary.domain.model.SkipSegmentPolicy
import com.singam.lionlibrary.domain.model.SkipSegmentType
import com.singam.lionlibrary.domain.repository.SkipSegmentRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Skip segments for the file currently playing in one player. Chapters apply as soon as the engine
 * reports them; the online lookup starts once a positive duration is known and never delays
 * playback. A new file or turning online lookups off cancels and discards an in-flight lookup. A
 * duration change beyond the tolerance revalidates once per file.
 *
 * Not thread-safe: call it from the [scope]'s (main) thread only.
 */
class SkipSegmentSession(
    private val repository: SkipSegmentRepository,
    private val scope: CoroutineScope,
) {
    private val mutableSegments = MutableStateFlow<List<SkipSegment>>(emptyList())
    val segments: StateFlow<List<SkipSegment>> = mutableSegments.asStateFlow()

    private var key: SkipLookupKey? = null
    private var chapters: List<PlaybackChapter> = emptyList()
    private var chapterSegments: List<SkipSegment> = emptyList()
    private var online: List<SkipSegment> = emptyList()
    private var durationMs = 0L
    private var onlineEnabled = false
    private var lookupDurationMs: Long? = null
    private var revalidated = false
    private var lookup: Job? = null
    private var generation = 0L

    /** Starts a new file; [key] is null when it can use chapters only. */
    fun start(key: SkipLookupKey?) {
        cancelLookup()
        this.key = key
        chapters = emptyList()
        chapterSegments = emptyList()
        online = emptyList()
        durationMs = 0
        lookupDurationMs = null
        revalidated = false
        publish()
    }

    /** Latest engine facts for the current file. A non-positive duration is still unknown. */
    fun onPlayback(durationMs: Long, chapters: List<PlaybackChapter>) {
        val newDuration = durationMs > 0 && durationMs != this.durationMs
        if (newDuration) this.durationMs = durationMs
        if (newDuration || chapters != this.chapters) {
            this.chapters = chapters
            chapterSegments = SkipSegmentPolicy.fromChapters(chapters, this.durationMs)
            publish()
        }
        maybeLookup()
    }

    fun setOnlineEnabled(enabled: Boolean) {
        if (enabled == onlineEnabled) return
        onlineEnabled = enabled
        if (enabled) maybeLookup()
        else {
            cancelLookup()
            online = emptyList()
            lookupDurationMs = null
            revalidated = false
            publish()
        }
    }

    private fun maybeLookup() {
        val current = key ?: return
        if (!onlineEnabled || durationMs <= 0) return
        val previous = lookupDurationMs
        when {
            previous == null -> launchLookup(current, durationMs)
            !revalidated && SkipSegmentPolicy.durationChanged(previous, durationMs) -> {
                revalidated = true
                launchLookup(current, durationMs)
            }
        }
    }

    private fun launchLookup(current: SkipLookupKey, duration: Long) {
        cancelLookup()
        lookupDurationMs = duration
        if (online.isNotEmpty()) {
            online = emptyList()
            publish()
        }
        // Types already named by chapters need no online request.
        val types = SkipSegmentType.entries.toSet() - chapterSegments.map { it.type }.toSet()
        if (types.isEmpty()) return
        val requested = generation
        lookup = scope.launch {
            val result = repository.onlineSegments(current, duration, types)
            if (requested == generation && result is OperationResult.Success) {
                online = result.data
                publish()
            }
        }
    }

    private fun cancelLookup() {
        generation++
        lookup?.cancel()
        lookup = null
    }

    private fun publish() {
        mutableSegments.value = SkipSegmentPolicy.combine(chapterSegments, online)
    }
}
