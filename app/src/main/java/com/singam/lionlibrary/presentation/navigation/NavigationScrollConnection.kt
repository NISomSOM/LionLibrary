package com.singam.lionlibrary.presentation.navigation

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import kotlin.math.abs

/**
 * Watches vertical scrolling of any page below it. Content moving up by more than [thresholdPx]
 * reports compact = true; moving down, or a pull past the top, reports false. Horizontal rows only
 * produce x deltas and are ignored. Consumes nothing.
 */
internal class NavigationScrollConnection(
    private val thresholdPx: Float = 24f,
    private val onCompactChange: (Boolean) -> Unit,
) : NestedScrollConnection {
    private var travel = 0f
    private var compact = false

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        if (available.y > 0f) {
            // At the top and still pulling down.
            set(false)
            return Offset.Zero
        }
        val dy = consumed.y
        if (dy == 0f) return Offset.Zero
        // Start counting again when the direction changes.
        if ((dy < 0f) != (travel < 0f)) travel = 0f
        travel += dy
        if (abs(travel) >= thresholdPx) set(travel < 0f)
        return Offset.Zero
    }

    /** A new destination always starts with the full bar. */
    fun reset() {
        compact = false
        travel = 0f
    }

    private fun set(value: Boolean) {
        if (value == compact) return
        compact = value
        travel = 0f
        onCompactChange(value)
    }
}
