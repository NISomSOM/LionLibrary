package com.singam.lionlibrary.presentation.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.singam.lionlibrary.R
import com.singam.lionlibrary.domain.model.MediaItem
import com.singam.lionlibrary.presentation.details.prewarmAmbientBackdrop
import com.singam.lionlibrary.presentation.navigation.LocalPosterTapOrigin
import com.singam.lionlibrary.presentation.navigation.posterCardSharedBounds
import com.singam.lionlibrary.ui.theme.LionLayout
import com.singam.lionlibrary.ui.theme.LionTheme
import com.singam.lionlibrary.ui.theme.cardEdge

/**
 * [sharedShelf] names the shelf or grid this card sits in. With it, the poster is shared with the
 * Details page a tap opens: it flies out of this card to the top of the page and back. A touch also
 * starts decoding the title's logo and blurred backdrop, so the page has them ready.
 */
@Composable
fun MediaCard(
    mediaItem: MediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onToggleWatchlist: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    sharedShelf: String? = null,
) {
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    val tapOrigin = LocalPosterTapOrigin.current
    val sharedBounds = posterCardSharedBounds(mediaItem.id, sharedShelf)
    val longPress = rememberLongPressSource()
    val posterPath = mediaItem.posterPath
    val logoPath = mediaItem.logoPath
    // The card dips a little under the finger and springs back, so a touch is answered at once.
    val interactionSource = remember { MutableInteractionSource() }
    var artworkSize by remember { mutableStateOf(Size.Zero) }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by
        animateFloatAsState(
            if (pressed) PRESSED_SCALE else 1f,
            spring(dampingRatio = .6f, stiffness = Spring.StiffnessMedium),
            label = "cardPress",
        )
    Surface(
        modifier =
            modifier
                .width(LionLayout.posterCardWidth)
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                }
                .pointerInput(posterPath, logoPath, sharedShelf) {
                    if (sharedShelf == null) return@pointerInput
                    awaitEachGesture {
                        // Observe only: the click and long press below still get the touch.
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        posterPath?.let { prewarmAmbientBackdrop(context, it) }
                        logoPath?.let { prewarmArtwork(context, it) }
                    }
                }
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    onClick = {
                        if (sharedShelf != null && posterPath != null) {
                            tapOrigin?.onCardTapped(
                                sharedShelf,
                                posterPath,
                                artworkSize.takeIf { it != Size.Zero },
                            )
                        }
                        onClick()
                    },
                    onLongClick =
                        onLongClick?.let { handler ->
                            {
                                longPress.capture()
                                handler()
                            }
                        },
                ),
        // Only artwork is rounded: rounding the caption cuts the year at the bottom corners.
        shape = RectangleShape,
        color = Color.Transparent,
    ) {
        Column {
            Box(
                Modifier.fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .then(longPress.modifier)
                    .onSizeChanged { artworkSize = it.toSize() }
                    .then(sharedBounds)
                    .clip(RoundedCornerShape(LionLayout.CardRadius))
                    .cardEdge(LionLayout.CardRadius, LionTheme.colors)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                LocalArtwork(
                    listOf(mediaItem.posterPath, mediaItem.backdropPath),
                    null,
                    Modifier.fillMaxSize(),
                    fallback = {
                        Icon(
                            Icons.Outlined.Movie,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .4f),
                            modifier = Modifier.size(36.dp),
                        )
                    },
                )
                if (mediaItem.isUnidentified) {
                    Surface(
                        Modifier.align(Alignment.BottomStart).padding(8.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = .95f),
                        shape = RoundedCornerShape(6.dp),
                    ) {
                        Text(
                            stringResource(R.string.local_file),
                            Modifier.padding(6.dp),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                onToggleWatchlist?.let { toggle ->
                    IconButton(
                        onClick = {
                            haptics.performHapticFeedback(
                                if (mediaItem.isWatchlisted) HapticFeedbackType.ToggleOff
                                else HapticFeedbackType.ToggleOn
                            )
                            toggle()
                        },
                        modifier = Modifier.align(Alignment.TopEnd),
                    ) {
                        Icon(
                            if (mediaItem.isWatchlisted) Icons.Filled.Bookmark
                            else Icons.Outlined.BookmarkBorder,
                            modifier =
                                Modifier.size(LionLayout.BookmarkBadge)
                                    .background(
                                        MaterialTheme.colorScheme.scrim.copy(alpha = .65f),
                                        CircleShape,
                                    )
                                    .padding(
                                        (LionLayout.BookmarkBadge - LionLayout.BookmarkGlyph) / 2
                                    ),
                            contentDescription =
                                stringResource(
                                    if (mediaItem.isWatchlisted) R.string.remove_watchlist
                                    else R.string.add_watchlist
                                ),
                            tint =
                                if (mediaItem.isWatchlisted) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onBackground,
                        )
                    }
                }
            }
            Text(
                mediaItem.title,
                Modifier.padding(top = LionLayout.CaptionGap),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            mediaItem.year?.let {
                Text(
                    it.toString(),
                    Modifier.padding(top = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Size of a poster card while a finger is on it. */
private const val PRESSED_SCALE = .955f
