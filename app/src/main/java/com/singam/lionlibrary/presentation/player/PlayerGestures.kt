package com.singam.lionlibrary.presentation.player

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange

/** What the player does with a recognised gesture. Implemented by the player screen. */
@Stable
internal interface PlayerGestureHandler {
    fun onTap()

    /** Double tap on the left or right third (never the centre). */
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
 * - tap toggles controls; double tap seeks on the left/right thirds;
 * - holding still plays at 2x until release (never while moving or locked);
 * - a horizontal drag seeks; a clearly vertical drag changes volume (left) or brightness (right);
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
            val width = size.width.toFloat()
            val height = size.height.toFloat()
            val zone = PlayerGestureMath.zoneOf(down.position.x, width)
            val systemStart =
                PlayerGestureMath.startsInSystemZone(
                    down.position.x,
                    down.position.y,
                    width,
                    height,
                    exclusion(),
                )
            val start =
                withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    classify(down.id, down.position, viewConfiguration.touchSlop)
                } ?: Start.Hold
            when (start) {
                Start.Tap ->
                    if (zone == GestureZone.CENTER) handler.onTap()
                    else {
                        val second =
                            withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
                                awaitFirstDown(requireUnconsumed = false)
                            }
                        if (second == null) handler.onTap()
                        else {
                            waitForUpOrCancellation()
                            handler.onDoubleTap(zone)
                        }
                    }
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
                        systemStart -> trackUntilUp(down.id) {}
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
                Start.Cancelled -> Unit
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
        if (!change.pressed) return if (change.isConsumed) Start.Cancelled else Start.Tap
        val offset = change.position - origin
        val axis = PlayerGestureMath.axisFor(offset.x, offset.y, slop)
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
