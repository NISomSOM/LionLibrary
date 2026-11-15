package com.singam.lionlibrary.presentation.player

import com.singam.lionlibrary.domain.model.PlaybackSnapshot
import com.singam.lionlibrary.domain.model.PlaybackTarget
import com.singam.lionlibrary.domain.model.SkipSegment
import com.singam.lionlibrary.domain.model.SkipSegmentPolicy
import com.singam.lionlibrary.domain.model.SkipSegmentType

/** Pure decisions the player ViewModel makes about saving progress and the skip button. */
internal object PlayerProgressPolicy {
    /**
     * The snapshot to queue for [target], captured before any suspension. The engine's live values
     * win over the last ticked ones. An untouched file (position 0, not completed) saves nothing,
     * so opening and leaving a title never creates a fake "started" history row.
     */
    fun snapshot(
        target: PlaybackTarget?,
        enginePositionMs: Long?,
        lastPositionMs: Long,
        engineDurationMs: Long?,
        lastDurationMs: Long,
        completed: Boolean,
        nowMs: Long,
    ): PlaybackSnapshot? {
        val current = target ?: return null
        val position = (enginePositionMs ?: lastPositionMs).coerceAtLeast(0)
        if (position == 0L && !completed) return null
        return PlaybackSnapshot(
            current.mediaId,
            current.episodeId,
            position,
            engineDurationMs ?: lastDurationMs,
            completed,
            nowMs,
        )
    }

    /**
     * Skip button for the segment under [positionMs]; none while unseekable, ended or switching.
     */
    fun skipButton(
        segments: List<SkipSegment>,
        positionMs: Long,
        durationMs: Long,
        seekable: Boolean,
        ended: Boolean,
        transitioning: Boolean,
    ): SkipSegmentType? =
        if (!seekable || ended || transitioning) null
        else SkipSegmentPolicy.active(segments, positionMs, durationMs)?.type
}
