package com.singam.lionlibrary.presentation.components

import android.os.Build
import android.view.Window
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.node.Ref
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.util.lerp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.singam.lionlibrary.R
import com.singam.lionlibrary.domain.model.MediaItem
import com.singam.lionlibrary.ui.theme.LionLayout
import com.singam.lionlibrary.ui.theme.LionLibraryTheme
import com.singam.lionlibrary.ui.theme.LionTheme
import com.singam.lionlibrary.ui.theme.cardEdge
import com.singam.lionlibrary.ui.theme.rememberSystemMotionEnabled
import kotlinx.coroutines.launch

/** Window dim behind the preview; the only effect when blur is unavailable. */
private const val PREVIEW_DIM = .6f

/** Blur radius behind the preview on Android 12+, in pixels. */
private const val PREVIEW_BLUR_PX = 48

private const val PREVIEW_CLOSE_MS = 180

/**
 * Where the artwork of the card under a long press sits on screen, handed from the card to the
 * [LongPressPreview] that the press opens, so the preview's artwork can lift out of that card. A
 * preview opened another way (hero carousel, accessibility action) finds nothing and rises in
 * place.
 */
@Stable
class LongPressOrigin {
    private var pending: Rect? = null

    /** Called by a card as its long press fires. */
    fun capture(coordinates: LayoutCoordinates?) {
        pending =
            coordinates
                ?.takeIf { it.isAttached }
                ?.let { Rect(it.positionOnScreen(), it.size.toSize()) }
    }

    /** The pressed card's artwork bounds, once. */
    fun consume(): Rect? = pending.also { pending = null }
}

val LocalLongPressOrigin = staticCompositionLocalOf<LongPressOrigin?> { null }

/**
 * Remembers where a card's artwork is laid out, for [LongPressOrigin.capture]: put [modifier] on
 * the artwork and call [capture] from the card's long click.
 */
@Stable
class LongPressSource internal constructor(private val origin: LongPressOrigin?) {
    private val coordinates = Ref<LayoutCoordinates>()
    val modifier: Modifier = Modifier.onGloballyPositioned { coordinates.value = it }

    fun capture() {
        origin?.capture(coordinates.value)
    }
}

@Composable
fun rememberLongPressSource(): LongPressSource {
    val origin = LocalLongPressOrigin.current
    return remember(origin) { LongPressSource(origin) }
}

/**
 * Long-press preview: the pressed card's artwork lifts out of its place to the middle of the screen
 * with a spring while the page behind blurs and dims (Android 12+ with cross-window blur enabled;
 * dim only otherwise). [title], [subtitle] and [description] sit below it, then the [actions].
 * Tapping outside or Back sends it back the way it came. Destructive actions keep their own
 * confirmation dialogs.
 */
@Composable
fun LongPressPreview(
    title: String,
    onDismiss: () -> Unit,
    artworkAspectRatio: Float,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    description: String? = null,
    onArtworkClick: (() -> Unit)? = null,
    artwork: @Composable BoxScope.() -> Unit,
    actions: @Composable ColumnScope.() -> Unit,
) {
    val motion = rememberSystemMotionEnabled()
    val pressOrigin = LocalLongPressOrigin.current
    val origin = remember { pressOrigin?.consume().takeIf { motion } }
    val lift = remember { Animatable(if (motion) 0f else 1f) }
    val scope = rememberCoroutineScope()
    var closing by remember { mutableStateOf(false) }
    val close: () -> Unit = {
        if (!closing) {
            closing = true
            scope.launch {
                if (motion) lift.animateTo(0f, tween(PREVIEW_CLOSE_MS))
                onDismiss()
            }
        }
    }
    Dialog(
        onDismissRequest = close,
        properties =
            DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        PreviewWindowEffects { lift.value.coerceIn(0f, 1f) }
        // Where the artwork ends up, on screen: the lift starts once this is known.
        var target by remember { mutableStateOf<Rect?>(null) }
        LaunchedEffect(target != null) {
            if (motion && (target != null || origin == null)) {
                lift.animateTo(1f, spring(dampingRatio = .78f, stiffness = 420f))
            }
        }
        Box(
            modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = close,
                )
                .safeDrawingPadding()
                .padding(LionLayout.SpaceXl),
            contentAlignment = Alignment.Center,
        ) {
            // Tablets get a larger card; phone sizes look lost on a big screen.
            val tablet = LionLayout.isTablet
            Column(
                Modifier.widthIn(max = if (tablet) 480.dp else 360.dp)
                    .verticalScroll(rememberScrollState())
                    .semantics {
                        paneTitle = title
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(LionLayout.SpaceL),
            ) {
                val wide = artworkAspectRatio > 1f
                Box(
                    Modifier.widthIn(
                            max =
                                when {
                                    wide && tablet -> 500.dp
                                    wide -> 320.dp
                                    tablet -> 420.dp
                                    else -> 220.dp
                                }
                        )
                        .fillMaxWidth(if (wide) .88f else .62f)
                        .aspectRatio(artworkAspectRatio)
                        .onGloballyPositioned {
                            if (it.isAttached) {
                                target = Rect(it.positionOnScreen(), it.size.toSize())
                            }
                        }
                        .graphicsLayer {
                            val progress = lift.value
                            val end = target
                            if (origin != null) {
                                // Out of the pressed card: same picture, growing from the card's
                                // place and size to this one.
                                if (end == null) {
                                    alpha = 0f
                                } else {
                                    val scale = lerp(origin.width / end.width, 1f, progress)
                                    scaleX = scale
                                    scaleY = scale
                                    translationX =
                                        (origin.center.x - end.center.x) * (1f - progress)
                                    translationY =
                                        (origin.center.y - end.center.y) * (1f - progress)
                                }
                            } else {
                                scaleX = .82f + .18f * progress
                                scaleY = .82f + .18f * progress
                                translationY = (1f - progress) * 48.dp.toPx()
                                alpha = progress.coerceIn(0f, 1f)
                            }
                            shadowElevation = 24.dp.toPx() * progress.coerceIn(0f, 1f)
                            shape = RoundedCornerShape(LionLayout.CardRadius)
                            clip = true
                        }
                        .clip(RoundedCornerShape(LionLayout.CardRadius))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .cardEdge(LionLayout.CardRadius, LionTheme.colors)
                        .then(
                            if (onArtworkClick != null) {
                                Modifier.clickable(role = Role.Button, onClick = onArtworkClick)
                            } else {
                                Modifier
                            }
                        ),
                    contentAlignment = Alignment.Center,
                    content = artwork,
                )
                Column(
                    Modifier.fillMaxWidth().graphicsLayer { alpha = lift.value.coerceIn(0f, 1f) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(LionLayout.SpaceXs),
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        color = LionTheme.colors.onMedia,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = LionTheme.colors.textSecondary,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                if (!description.isNullOrBlank()) {
                    Text(
                        description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = LionTheme.colors.onMedia,
                        modifier =
                            Modifier.fillMaxWidth().graphicsLayer {
                                alpha = lift.value.coerceIn(0f, 1f)
                            },
                    )
                }
                Surface(
                    shape = RoundedCornerShape(LionLayout.RadiusL),
                    color = LionTheme.colors.sheet,
                    modifier =
                        Modifier.fillMaxWidth().graphicsLayer {
                            val progress = lift.value.coerceIn(0f, 1f)
                            alpha = progress
                            translationY = (1f - progress) * 24.dp.toPx()
                        },
                ) {
                    Column(Modifier.padding(vertical = LionLayout.SpaceS), content = actions)
                }
            }
        }
    }
}

/**
 * Long-press preview of a poster card (Home shelves and hero, Library grid): the poster, the title
 * and year, then mark as watched, the watchlist bookmark and delete from device. Tapping the poster
 * opens the title.
 */
@Composable
fun MediaItemPreview(
    media: MediaItem,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    onMarkWatched: () -> Unit,
    onToggleWatchlist: () -> Unit,
    onDelete: () -> Unit,
) {
    LongPressPreview(
        title = media.title,
        onDismiss = onDismiss,
        artworkAspectRatio = 2f / 3f,
        subtitle = media.year?.toString(),
        onArtworkClick = onOpen,
        artwork = {
            LocalArtwork(
                listOf(media.posterPath, media.backdropPath),
                null,
                Modifier.fillMaxSize(),
                fallback = {
                    Icon(
                        Icons.Outlined.Movie,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
        },
    ) {
        MediaOptionItem(
            Icons.Default.CheckCircle,
            stringResource(R.string.ui_mark_as_watched),
            onMarkWatched,
        )
        MediaOptionItem(
            if (media.isWatchlisted) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
            stringResource(
                if (media.isWatchlisted) R.string.remove_watchlist else R.string.add_watchlist
            ),
            onToggleWatchlist,
        )
        MediaOptionItem(
            Icons.Default.Delete,
            stringResource(R.string.delete_from_device),
            onDelete,
            iconTint = MaterialTheme.colorScheme.error,
        )
    }
}

/**
 * Dim and, on Android 12+ when the device has cross-window blur on, blur behind the window, both
 * following [progress] so the page eases out of focus as the artwork lifts.
 */
@Composable
private fun PreviewWindowEffects(progress: () -> Float) {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window ?: return
    LaunchedEffect(window) {
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        val blur =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                window.windowManager.isCrossWindowBlurEnabled
        if (blur) window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        snapshotFlow(progress).collect { value -> window.applyFocus(value, blur) }
    }
}

private fun Window.applyFocus(progress: Float, blur: Boolean) {
    setDimAmount(PREVIEW_DIM * progress)
    if (blur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        attributes = attributes.apply { blurBehindRadius = (PREVIEW_BLUR_PX * progress).toInt() }
    }
}

@Preview(name = "Poster options")
@Composable
private fun MediaItemPreviewPreview() {
    LionLibraryTheme { MediaItemPreview(PreviewFilm, {}, {}, {}, {}, {}) }
}
