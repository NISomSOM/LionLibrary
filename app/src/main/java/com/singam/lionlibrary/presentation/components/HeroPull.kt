package com.singam.lionlibrary.presentation.components

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import kotlin.math.roundToInt

/** Pull distance kept per pixel dragged past the top (before it tightens), and its cap. */
private const val HERO_PULL_RESISTANCE = .55f
private const val HERO_PULL_MAX_PX = 420f

/** The return spring ends within half a pixel of rest instead of chasing hundredths. */
private const val PULL_SETTLE_PX = .5f

/**
 * Rubber band at the top of a list whose first item is a hero (Details, Home carousel): a pull past
 * the top makes the hero taller by [pull] pixels, so the page follows the finger with growing
 * resistance while the picture stretches to fill the gap, and a spring returns it when the finger
 * lifts. Put the connection on the list with `nestedScroll` and [stretchedHeight] on the hero. When
 * not [enabled] (system animations off, layouts without a fixed-height hero) nothing is pulled.
 */
@Composable
fun rememberHeroPullConnection(pull: MutableFloatState, enabled: Boolean): NestedScrollConnection =
    remember(pull, enabled) { HeroPullConnection(pull, enabled) }

private class HeroPullConnection(
    private val pull: MutableFloatState,
    private val enabled: Boolean,
) : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        val current = pull.floatValue
        if (current <= 0f || available.y >= 0f) return Offset.Zero
        val used = maxOf(available.y, -current)
        pull.floatValue = current + used
        return Offset(0f, used)
    }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        if (!enabled || source != NestedScrollSource.UserInput) return Offset.Zero
        if (available.y <= 0f) return Offset.Zero
        // The further it is pulled, the less it gives.
        val current = pull.floatValue
        val give = HERO_PULL_RESISTANCE * (1f - current / HERO_PULL_MAX_PX)
        pull.floatValue = (current + available.y * give).coerceAtMost(HERO_PULL_MAX_PX)
        return Offset(0f, available.y)
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (pull.floatValue <= 0f) return Velocity.Zero
        animate(
            pull.floatValue,
            0f,
            animationSpec =
                spring(
                    dampingRatio = .82f,
                    stiffness = 320f,
                    visibilityThreshold = PULL_SETTLE_PX,
                ),
        ) { value, _ ->
            // Never below the resting height: the spring's tiny overshoot would shrink the hero.
            pull.floatValue = value.coerceAtLeast(0f)
        }
        pull.floatValue = 0f
        // The release belongs to the pull. Handing its speed on to the list (already at the top)
        // turned into the system's stretch overscroll once the spring had finished: a second small
        // bounce about half a second after the hero settled.
        return available
    }
}

/** Fixed hero height plus the current pull, applied in layout so a pull never recomposes. */
fun Modifier.stretchedHeight(base: Dp, pullPx: () -> Float): Modifier =
    layout { measurable, constraints ->
        val height = base.roundToPx() + pullPx().roundToInt()
        val placeable = measurable.measure(constraints.copy(minHeight = height, maxHeight = height))
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
