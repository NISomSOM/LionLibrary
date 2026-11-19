package com.singam.lionlibrary.presentation.player

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange

/** What the player does with a recognised gesture. Implemented by the player screen. */
@Stable
internal interface PlayerGestureHandler {
    fun onTap()

    /** Double tap: the left/right thirds seek, the centre third pauses or resumes. */
    fun onDoubleTap(zone: GestureZone)

    /** Finger held still for the long-press timeout; [onHoldEnd] follows on release. */
    fun onHoldStart()

    fun onHoldEnd()

    fun onSeekDragStart()

    /** Total horizontal travel since the drag started, in pixels of a [widthPx]-wide surface. */
    fun onSeekDrag(totalDxPx: Float, widthPx: Float)

    fun onSeekDragEnd()

    /** Vertical drag in the left or right third; [deltaLevel] is the change since the last call. */
    fun onLevelDrag(zone: GestureZone, deltaLevel: Float)

    fun onLevelDragEnd(zone: GestureZone)
}

private sealed interface Start {
    data object Tap : Start

    data object Hold : Start

    data class Drag(val axis: DragAxis, val origin: Offset) : Start

    data object Cancelled : Start
}

/**
 * One handler for every touch on the video, replacing separate per-third detectors:
 * - tap toggles controls; double tap seeks on the left/right thirds and pauses/resumes in the
 *   centre (the second touch must itself be a tap: a swipe right after a tap still seeks);
 * - holding still plays at 2x until release (never while moving or locked);
 * - a horizontal drag seeks; a clearly vertical drag changes brightness (left) or volume (right);
 * - drags that start inside system edges/bars ([exclusion]) are left to the system.
 *
 * When [locked], only taps are recognised (they reveal the unlock button).
 */
internal fun Modifier.playerGestures(
    locked: Boolean,
    exclusion: () -> GestureExclusion,
    handler: PlayerGestureHandler,
): Modifier =
    pointerInput(locked, handler) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (locked) {
                if (waitForUpOrCancellation() != null) handler.onTap()
                return@awaitEachGesture
            }
            val start = start(down)
            if (start != Start.Tap) {
                follow(down, start, exclusion(), handler)
                return@awaitEachGesture
            }
            // Every third waits briefly for a second tap: the sides seek, the centre
            // pauses/resumes. A lone tap then toggles the controls.
            val second =
                withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
                    awaitFirstDown(requireUnconsumed = false)
                }
            if (second == null) {
                handler.onTap()
                return@awaitEachGesture
            }
            // Only a second tap makes a double tap. A swipe or hold that starts right after a
            // tap is its own gesture, so seeking never pauses or jumps 10 s instead.
            val secondStart = start(second)
            if (secondStart == Start.Tap)
                handler.onDoubleTap(PlayerGestureMath.zoneOf(down.position.x, size.width.toFloat()))
            else follow(second, secondStart, exclusion(), handler)
        }
    }

/** Classifies the touch that began with [down]; staying still past the long-press timeout holds. */
private suspend fun AwaitPointerEventScope.start(down: PointerInputChange): Start =
    withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
        classify(down.id, down.position, viewConfiguration.touchSlop)
    } ?: Start.Hold

/** Runs a hold or drag that began with [down] until the finger lifts. */
private suspend fun AwaitPointerEventScope.follow(
    down: PointerInputChange,
    start: Start,
    exclusion: GestureExclusion,
    handler: PlayerGestureHandler,
) {
    val width = size.width.toFloat()
    val height = size.height.toFloat()
    val zone = PlayerGestureMath.zoneOf(down.position.x, width)
    when (start) {
        Start.Tap,
        Start.Cancelled -> Unit
        Start.Hold -> {
            handler.onHoldStart()
            try {
                trackUntilUp(down.id) {}
            } finally {
                handler.onHoldEnd()
            }
        }
        is Start.Drag ->
            when {
                PlayerGestureMath.startsInSystemZone(
                    down.position.x,
                    down.position.y,
                    width,
                    height,
                    exclusion,
                ) -> trackUntilUp(down.id) {}
                start.axis == DragAxis.HORIZONTAL -> {
                    handler.onSeekDragStart()
                    try {
                        trackUntilUp(down.id) { position ->
                            handler.onSeekDrag(position.x - start.origin.x, width)
                        }
                    } finally {
                        handler.onSeekDragEnd()
                    }
                }
                zone == GestureZone.CENTER -> trackUntilUp(down.id) {}
                else -> {
                    var last = start.origin.y
                    try {
                        trackUntilUp(down.id) { position ->
                            handler.onLevelDrag(
                                zone,
                                PlayerGestureMath.levelDelta(position.y - last, height),
                            )
                            last = position.y
                        }
                    } finally {
                        handler.onLevelDragEnd(zone)
                    }
                }
            }
    }
}

/** Waits until the pointer lifts (tap) or moves past the slop with a clear direction (drag). */
private suspend fun AwaitPointerEventScope.classify(
    id: PointerId,
    origin: Offset,
    slop: Float,
): Start {
    while (true) {
        val change =
            awaitPointerEvent().changes.firstOrNull { it.id == id } ?: return Start.Cancelled
        val offset = change.position - origin
        val axis = PlayerGestureMath.axisFor(offset.x, offset.y, slop)
        if (!change.pressed) {
            // A flick that lifts before any move past the slop was reported is not a tap.
            val moved = axis != null || offset.getDistance() > slop
            return if (change.isConsumed || moved) Start.Cancelled else Start.Tap
        }
        if (axis != null) {
            change.consume()
            return Start.Drag(axis, change.position)
        }
    }
}

/** Consumes movement of [id] until it lifts or is cancelled, reporting each new position. */
private suspend fun AwaitPointerEventScope.trackUntilUp(id: PointerId, onMove: (Offset) -> Unit) {
    while (true) {
        val change = awaitPointerEvent().changes.firstOrNull { it.id == id } ?: return
        if (!change.pressed) return
        if (change.positionChange() != Offset.Zero) {
            onMove(change.position)
            change.consume()
        }
    }
}
