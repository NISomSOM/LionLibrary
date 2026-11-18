package com.singam.lionlibrary.presentation.details

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.util.lerp
import com.singam.lionlibrary.presentation.components.LocalArtwork
import kotlin.math.roundToInt

/**
 * Closing a Details page that is scrolled so far that its poster is mostly off screen: rather than
 * flying a poster in from above the screen, the whole page shrinks into the card it was opened from
 * (a container transform, as in iOS's zoom transition), rounding its corners, while the card's
 * poster fades in over it. It lands on the part of the card that is not hidden behind a bar.
 *
 * The page keeps its width-to-content scale uniform (it only gets smaller, never squashed); its
 * height is cut by a clip that shrinks to the card's height. [progress] runs 0 to 1 over the close;
 * [target] is the card's visible artwork in root coordinates and [fullCard] its whole artwork, so
 * the poster lines up with the card's own picture.
 */
internal class PageShrink(
    private val progress: () -> Float,
    private val target: () -> Rect?,
    private val fullCard: () -> Rect?,
    private val cornerPx: Float,
) {
    internal var page: LayoutCoordinates? = null

    /** The card's poster over the page: in from a fifth of the way, solid by seven tenths. */
    val posterAlpha: Float
        get() = ((progress() - POSTER_FADE_START) / POSTER_FADE_SPAN).coerceIn(0f, 1f)

    /** One frame of the shrink in the page's own (unscaled) coordinates, or null when at rest. */
    class Frame(
        val scale: Float,
        val dx: Float,
        val dy: Float,
        /** Height of the visible part, from the page's top. */
        val clipHeight: Float,
        val corner: Float,
        /** The poster's height and top, so it ends exactly on the card's picture. */
        val posterHeight: Float,
        val posterTop: Float,
    )

    fun frame(size: Size): Frame? {
        val p = progress()
        val to = target()
        val card = fullCard()
        val origin = page?.takeIf { it.isAttached }?.positionInRoot()
        if (p <= 0f || to == null || card == null || origin == null || size.width <= 0f) {
            return null
        }
        val width = lerp(size.width, to.width, p)
        val height = lerp(size.height, to.height, p)
        val scale = width / size.width
        val left = lerp(origin.x, to.left, p) - origin.x
        val top = lerp(origin.y, to.top, p) - origin.y
        val clipHeight = height / scale
        // The poster grows from the page's full height to the whole card; its bottom stays on the
        // clip's bottom, so a card cut off at the top shows the same lower part of the picture.
        val posterHeight = lerp(size.height, card.height, p) / scale
        return Frame(
            scale = scale,
            dx = left,
            dy = top,
            clipHeight = clipHeight,
            corner = lerp(0f, cornerPx, p) / scale,
            posterHeight = posterHeight,
            posterTop = clipHeight - posterHeight,
        )
    }
}

/** On the page's root, before its background: records where the page is and applies the shrink. */
internal fun Modifier.pageShrink(shrink: PageShrink): Modifier = onGloballyPositioned {
    shrink.page = it
}
    .graphicsLayer {
        val f = shrink.frame(size) ?: return@graphicsLayer
        transformOrigin = TransformOrigin(0f, 0f)
        scaleX = f.scale
        scaleY = f.scale
        translationX = f.dx
        translationY = f.dy
        shape = GenericShape { box, _ ->
            addRoundRect(RoundRect(0f, 0f, box.width, f.clipHeight, CornerRadius(f.corner)))
        }
        clip = true
    }

/**
 * The card's poster over the shrinking page (last child of the page's root), fading in so the page
 * lands as the card's own picture. Nothing is drawn while the page is at rest.
 */
@Composable
internal fun PageShrink.ShrinkPoster(posterPath: String) {
    Box(
        Modifier.fillMaxWidth()
            .layout { measurable, constraints ->
                val box = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
                val f = frame(box)
                val height = (f?.posterHeight ?: 0f).roundToInt().coerceAtLeast(0)
                val placeable = measurable.measure(Constraints.fixed(constraints.maxWidth, height))
                layout(constraints.maxWidth, constraints.maxHeight) {
                    placeable.place(0, (f?.posterTop ?: 0f).roundToInt())
                }
            }
            .graphicsLayer { alpha = posterAlpha },
        contentAlignment = Alignment.Center,
    ) {
        LocalArtwork(listOf(posterPath), null, Modifier.fillMaxSize())
    }
}

/** The poster starts to show this far into the shrink and is solid this much later. */
private const val POSTER_FADE_START = .2f
private const val POSTER_FADE_SPAN = .5f
