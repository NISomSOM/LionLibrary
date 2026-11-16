package com.singam.lionlibrary.presentation.navigation

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.util.lerp
import com.singam.lionlibrary.ui.theme.LionLayout
import com.singam.lionlibrary.ui.theme.rememberSystemMotionEnabled
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/** The destination's own enter/exit scope, provided inside each nav `composable {}`. */
val LocalNavAnimatedVisibilityScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Remembers which poster card opened Details and where every card sits on screen, so a Details page
 * can zoom out of its card and back into it. The same title sits on several shelves, so a card is
 * identified by shelf + title. Opening Details any other way (hero carousel, long-press preview)
 * leaves no key: plain fade.
 */
@Stable
class PosterZoomOrigin(initialKey: String? = null, initialBounds: Rect? = null) {
    /** Key of the card behind the newest Details page, or null for a plain fade. */
    var activeKey: String? by mutableStateOf(initialKey)
        private set

    /** True while Details is trading places with the player: those transitions only fade. */
    var suspended: Boolean by mutableStateOf(false)
        private set

    // Last known artwork rectangle of each card, in root coordinates. Plain map on purpose: it is
    // written on every layout pass and read only while a zoom is drawing its frames. Entries are
    // kept after a card leaves the screen, because a page may still be closing into it.
    private val cardBounds = HashMap<String, Rect>()
    private var tappedKey: String? = null

    /**
     * Still picture of the page a zoom is opening over (the whole window, taken at the tap), with
     * the window size it stands for. Navigation drops the old page almost at once; the opening page
     * draws this behind itself instead, so Back always meets a fully arrived page.
     */
    var backdrop: ImageBitmap? by mutableStateOf(null)
        private set

    var backdropWindowSize: IntSize = IntSize.Zero
        private set

    /** Set by the navigation host: takes the picture and reports it through [onBackdropReady]. */
    var requestBackdrop: (() -> Unit)? = null

    init {
        if (initialKey != null && initialBounds != null) cardBounds[initialKey] = initialBounds
    }

    /** Details should zoom from/into a card for the transition that is about to run. */
    val zoomActive: Boolean
        get() =
            windowSnapshotSupported && activeKey?.let(cardBounds::containsKey) == true && !suspended

    /**
     * Where the card with [key] sits. A page asks for its own card, so a page that is still closing
     * keeps heading for its card after another title has been opened.
     */
    fun boundsOf(key: String): Rect? = cardBounds[key]

    /** A poster card was tapped; navigation follows in the same click. */
    fun onCardTapped(key: String) {
        tappedKey = key
    }

    /** Details is about to open: adopt the tapped card, or nothing if no card started this. */
    fun onDetailsOpening() {
        activeKey = tappedKey
        suspended = false
        tappedKey = null
        backdrop = null
        if (zoomActive) requestBackdrop?.invoke()
    }

    fun onBackdropReady(image: ImageBitmap, windowSize: IntSize) {
        backdrop = image
        backdropWindowSize = windowSize
    }

    /** The opening zoom has arrived (or the page left): the picture is no longer needed. */
    fun onBackdropDone() {
        backdrop = null
    }

    /** A card was laid out (first time, scrolling, Home returning). */
    fun onCardPlaced(key: String, bounds: Rect) {
        cardBounds[key] = bounds
    }

    /** Details is about to be covered by the player. */
    fun onPlayerOpening() {
        suspended = true
    }

    /** Details is fully on screen again. */
    fun onDetailsSettled() {
        suspended = false
    }
}

@Composable
fun rememberPosterZoomOrigin(): PosterZoomOrigin =
    rememberSaveable(
        saver =
            Saver(
                save = { origin ->
                    val key = origin.activeKey
                    val bounds = key?.let(origin::boundsOf)
                    if (key == null || bounds == null) null
                    else listOf(key, bounds.left, bounds.top, bounds.right, bounds.bottom)
                },
                restore = { saved ->
                    PosterZoomOrigin(
                        saved[0] as String,
                        Rect(
                            saved[1] as Float,
                            saved[2] as Float,
                            saved[3] as Float,
                            saved[4] as Float,
                        ),
                    )
                },
            )
    ) {
        PosterZoomOrigin()
    }

val LocalPosterZoomOrigin = staticCompositionLocalOf<PosterZoomOrigin?> { null }

/** Key of one poster card: unique per shelf, so twin cards of a title are told apart. */
fun posterArtworkKey(shelf: String, mediaId: Long): String = "artwork:$shelf:$mediaId"

/**
 * One poster card's link to the page zoom: [modifier] goes on the artwork, [onTapped] is called
 * from the card's click before navigation. The card itself never moves or scales.
 */
@Stable
class PosterZoomSource
internal constructor(private val origin: PosterZoomOrigin?, private val key: String?) {
    val modifier: Modifier =
        if (origin == null || key == null) Modifier
        else Modifier.onGloballyPositioned { origin.onCardPlaced(key, it.fullBoundsInRoot()) }

    fun onTapped() {
        if (origin != null && key != null) origin.onCardTapped(key)
    }
}

@Composable
fun rememberPosterZoomSource(key: String?): PosterZoomSource {
    val origin = LocalPosterZoomOrigin.current
    return remember(origin, key) { PosterZoomSource(origin, key) }
}

/**
 * Details side of the page zoom. [backdropModifier] goes on a full-size box behind the page and
 * [modifier] on the page root inside it; [posterAlpha] drives a copy of the title's poster laid
 * over the top of the page (full width, 2:3) while [isClosing].
 */
@Stable
class PosterZoom
internal constructor(
    val backdropModifier: Modifier,
    val modifier: Modifier,
    val isClosing: Boolean,
    val posterAlpha: () -> Float,
)

/**
 * When a poster card opened this page, the whole page (backdrop, logo, buttons, text) scales up out
 * of that card's rectangle as one piece, clipped like the card while small. The poster itself stays
 * put, so no enlarged poster ever covers the real backdrop. Opening waits for [pageReady] (or a
 * short timeout) before it moves, so the page is finished in every frame of the zoom. The page it
 * grows over is a dimmed still picture of the window taken at the tap.
 *
 * On Back or predictive back the page shrinks into the card again, over the live page underneath.
 * During the second half of that move the poster fades in over the page's top, so what lands on the
 * card is the poster itself and the page can be dropped without anything vanishing.
 *
 * Returns null (plain navigation fades) for pages opened without a card, outside navigation and
 * with system animations off. Transitions to and from the player only fade.
 */
@Composable
fun rememberPosterZoom(pageReady: Boolean): PosterZoom? {
    val origin = LocalPosterZoomOrigin.current ?: return null
    val visibility = LocalNavAnimatedVisibilityScope.current ?: return null
    // This page's own card, fixed for the page's lifetime: opening another title while this one
    // is still closing must not send it to the other title's card.
    val key = remember { origin.activeKey } ?: return null
    if (!rememberSystemMotionEnabled()) return null

    val transition = visibility.transition
    val settled =
        transition.currentState == EnterExitState.Visible &&
            transition.targetState == EnterExitState.Visible
    LaunchedEffect(settled) { if (settled) origin.onDetailsSettled() }

    // Opening: 0 = the card's rectangle, 1 = full page. The page needs a moment to load its row
    // and decode the backdrop, so the zoom starts when the page is ready (or after a short
    // timeout), not at the tap: every frame of the move then shows the finished page.
    val opening = remember { Animatable(if (origin.suspended || !origin.zoomActive) 1f else 0f) }
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(pageReady) { if (pageReady) started = true }
    LaunchedEffect(Unit) {
        delay(READY_TIMEOUT_MS)
        started = true
    }
    LaunchedEffect(started) {
        if (started) {
            opening.animateTo(1f, spring(OPEN_DAMPING, OPEN_STIFFNESS))
            origin.onBackdropDone()
        }
    }
    // The still picture of the old page dims a little as soon as it is shown.
    val dim = remember { Animatable(0f) }
    LaunchedEffect(Unit) { dim.animateTo(1f, tween(BACKDROP_DIM_MS)) }

    // Closing (Back and predictive back): driven by the navigation transition itself.
    val closing =
        transition.animateFloat(
            transitionSpec = {
                // The page underneath is rebuilt during the first frames of a return; the
                // short hold lets that start behind the still-full page.
                if (targetState == EnterExitState.PostExit) {
                    tween(CLOSE_MS, delayMillis = CLOSE_HOLD_MS, easing = FastOutSlowInEasing)
                } else {
                    snap()
                }
            },
            label = "posterZoomClose",
        ) { state ->
            if (state == EnterExitState.PostExit) 0f else 1f
        }
    val isClosing = transition.targetState == EnterExitState.PostExit && !origin.suspended

    val posterAlpha = remember {
        { ((POSTER_FADE_START - closing.value) / POSTER_FADE_SPAN).coerceIn(0f, 1f) }
    }
    var pageOffset by remember { mutableStateOf(Offset.Zero) }
    val cornerPx = with(LocalDensity.current) { LionLayout.CardRadius.toPx() }
    val clip = remember { Path() }
    val scrim = MaterialTheme.colorScheme.background
    val backdropModifier = Modifier.drawBehind {
        val picture = origin.backdrop ?: return@drawBehind
        if (opening.value >= 1f || isClosing) return@drawBehind
        val window = origin.backdropWindowSize
        drawImage(
            picture,
            dstOffset = IntOffset(-pageOffset.x.roundToInt(), -pageOffset.y.roundToInt()),
            dstSize = window,
        )
        drawRect(scrim, alpha = dim.value * (1f - BACKDROP_BRIGHTNESS))
    }
    val modifier =
        Modifier.onGloballyPositioned { pageOffset = it.positionInRoot() }
            .graphicsLayer {
                val card = origin.boundsOf(key)
                if (card == null || origin.suspended) return@graphicsLayer
                val p = opening.value * closing.value
                if (p == 1f) return@graphicsLayer
                // Uniform scale pinned to the card's top-left: the page's width matches the
                // container's width all the way, so the hero grows exactly where the poster is.
                val scale = lerp(card.width / size.width, 1f, p)
                transformOrigin = TransformOrigin(0f, 0f)
                scaleX = scale
                scaleY = scale
                translationX = lerp(card.left - pageOffset.x, 0f, p)
                translationY = lerp(card.top - pageOffset.y, 0f, p)
                // Fades in while opening. Closing stays solid: it lands as the poster.
                alpha = (opening.value / OPEN_FADE_SPAN).coerceIn(0f, 1f)
            }
            .drawWithContent {
                val card = origin.boundsOf(key)
                val p = opening.value * closing.value
                if (card == null || origin.suspended || p >= 1f) {
                    drawContent()
                    return@drawWithContent
                }
                // Container = card rectangle growing to the full page, in page pixels.
                val t = p.coerceAtLeast(0f)
                val scale = lerp(card.width / size.width, 1f, p).coerceAtLeast(.01f)
                // While the poster fades in on a return, the container tightens to exactly the
                // poster (the card's shape at page width), so no page text shows beneath it.
                val posterHeight = size.width * card.height / card.width
                val height =
                    lerp(lerp(card.height, size.height, t) / scale, posterHeight, posterAlpha())
                val corner = cornerPx * (1f - t) / scale
                clip.reset()
                clip.addRoundRect(RoundRect(0f, 0f, size.width, height, CornerRadius(corner)))
                clipPath(clip) { this@drawWithContent.drawContent() }
            }
    return PosterZoom(backdropModifier, modifier, isClosing, posterAlpha)
}

/** Opening spring: fast with almost no overshoot, like a launcher opening an app. */
private const val OPEN_DAMPING = .9f
private const val OPEN_STIFFNESS = 1100f

/** Fraction of the opening travel over which the page fades in. */
private const val OPEN_FADE_SPAN = .12f

/** Longest wait for the page's row and backdrop before zooming anyway. */
private const val READY_TIMEOUT_MS = 160L

/** The old page's picture dims to this brightness behind an opening zoom. */
internal const val BACKDROP_BRIGHTNESS = .45f
private const val BACKDROP_DIM_MS = 120

/** Return: a brief hold while the page underneath starts rebuilding, then into the card. */
private const val CLOSE_HOLD_MS = 40
private const val CLOSE_MS = 250

/**
 * Closing progress (1 = full page, 0 = card) at which the poster starts to appear, and how long.
 */
private const val POSTER_FADE_START = .7f
private const val POSTER_FADE_SPAN = .5f

/**
 * Bounds in root coordinates without clipping: a card cut off by the screen edge still reports its
 * whole rectangle, so the page zooms to the card's real shape.
 */
private fun LayoutCoordinates.fullBoundsInRoot(): Rect = Rect(positionInRoot(), size.toSize())
