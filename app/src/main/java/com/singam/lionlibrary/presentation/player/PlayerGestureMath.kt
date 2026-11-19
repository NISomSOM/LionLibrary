package com.singam.lionlibrary.presentation.player

import kotlin.math.abs
import kotlin.math.roundToLong

/** Thirds of the video area: brightness/back-seek on the left, volume/forward-seek on the right. */
internal enum class GestureZone {
    LEFT,
    CENTER,
    RIGHT,
}

internal enum class DragAxis {
    HORIZONTAL,
    VERTICAL,
}

/** Areas, in pixels, where a drag must not start: system back edges and the top/bottom bars. */
internal data class GestureExclusion(
    val leftPx: Float = 0f,
    val rightPx: Float = 0f,
    val topPx: Float = 0f,
    val bottomPx: Float = 0f,
)

/** Pure decisions behind the player's single gesture handler. */
internal object PlayerGestureMath {
    private const val THIRTY_MINUTES_MS = 30 * 60_000L
    private const val SIXTY_MINUTES_MS = 60 * 60_000L

    /** A vertical drag must lead the horizontal movement by this factor before it counts. */
    const val VERTICAL_LEAD = 1.5f

    /** Brightness/volume change for a drag across the full height. */
    const val LEVEL_PER_HEIGHT = 2f

    /** Seek time covered by dragging across the whole width: about 60/90/120 s by duration. */
    fun seekWindowMs(durationMs: Long): Long =
        when {
            durationMs < THIRTY_MINUTES_MS -> 60_000L
            durationMs < SIXTY_MINUTES_MS -> 90_000L
            else -> 120_000L
        }

    /** Signed seek offset for a horizontal drag of [dragPx] on a [widthPx]-wide surface. */
    fun seekOffsetMs(dragPx: Float, widthPx: Float, durationMs: Long): Long {
        if (widthPx <= 0f || durationMs <= 0L || dragPx.isNaN()) return 0L
        return (dragPx / widthPx * seekWindowMs(durationMs)).roundToLong()
    }

    /** Target position for a drag that started at [startMs], kept inside the file. */
    fun seekTarget(startMs: Long, offsetMs: Long, durationMs: Long): Long {
        val target = startMs.coerceAtLeast(0L) + offsetMs
        return if (durationMs > 0) target.coerceIn(0L, durationMs) else target.coerceAtLeast(0L)
    }

    /**
     * The axis once the finger has moved past [slopPx]; null while it is still a tap/hold, or while
     * a diagonal movement has no clear direction yet. Vertical needs a [VERTICAL_LEAD] lead.
     */
    fun axisFor(dx: Float, dy: Float, slopPx: Float): DragAxis? {
        val ax = abs(dx)
        val ay = abs(dy)
        if (ax <= slopPx && ay <= slopPx) return null
        return when {
            ay >= ax * VERTICAL_LEAD -> DragAxis.VERTICAL
            ax >= ay -> DragAxis.HORIZONTAL
            else -> null
        }
    }

    fun zoneOf(x: Float, widthPx: Float): GestureZone =
        when {
            widthPx <= 0f -> GestureZone.CENTER
            x < widthPx / 3f -> GestureZone.LEFT
            x > widthPx * 2f / 3f -> GestureZone.RIGHT
            else -> GestureZone.CENTER
        }

    /** True when a touch starting here belongs to a system edge/bar gesture, not the player. */
    fun startsInSystemZone(
        x: Float,
        y: Float,
        widthPx: Float,
        heightPx: Float,
        exclusion: GestureExclusion,
    ): Boolean =
        x < exclusion.leftPx ||
            x > widthPx - exclusion.rightPx ||
            y < exclusion.topPx ||
            y > heightPx - exclusion.bottomPx

    /** Level change (0..1 scale) for a vertical movement; dragging up raises the level. */
    fun levelDelta(dyPx: Float, heightPx: Float): Float =
        if (heightPx <= 0f || dyPx.isNaN()) 0f else -dyPx / heightPx * LEVEL_PER_HEIGHT
}
