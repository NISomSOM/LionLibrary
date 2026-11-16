package com.singam.lionlibrary.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.singam.lionlibrary.R
import com.singam.lionlibrary.domain.model.MediaItem
import com.singam.lionlibrary.presentation.navigation.posterArtworkKey
import com.singam.lionlibrary.presentation.navigation.rememberPosterZoomSource
import com.singam.lionlibrary.ui.theme.LionLayout
import com.singam.lionlibrary.ui.theme.LionTheme
import com.singam.lionlibrary.ui.theme.cardEdge

/**
 * [sharedShelf] names the shelf or grid this card sits in. With it, a tap makes the Details page
 * zoom out of this card (and back into it); the card itself never moves. A touch also starts
 * decoding the title's backdrop, so the page has its hero ready when it appears.
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
    val zoomSource =
        rememberPosterZoomSource(sharedShelf?.let { posterArtworkKey(it, mediaItem.id) })
    val backdropPath = mediaItem.backdropPath ?: mediaItem.posterPath
    val logoPath = mediaItem.logoPath
    Surface(
        modifier =
            modifier
                .width(LionLayout.PosterWidth)
                .pointerInput(backdropPath, logoPath, sharedShelf) {
                    if (sharedShelf == null || backdropPath == null) return@pointerInput
                    awaitEachGesture {
                        // Observe only: the click and long press below still get the touch.
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        prewarmArtwork(context, backdropPath)
                        logoPath?.let { prewarmArtwork(context, it) }
                    }
                }
                .combinedClickable(
                    onClick = {
                        zoomSource.onTapped()
                        onClick()
                    },
                    onLongClick = onLongClick,
                ),
        // Only artwork is rounded: rounding the caption cuts the year at the bottom corners.
        shape = RectangleShape,
        color = Color.Transparent,
    ) {
        Column {
            Box(
                Modifier.fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .then(zoomSource.modifier)
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
