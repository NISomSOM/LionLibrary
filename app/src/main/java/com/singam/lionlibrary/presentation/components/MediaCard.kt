package com.singam.lionlibrary.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.singam.lionlibrary.R
import com.singam.lionlibrary.domain.model.MediaItem
import com.singam.lionlibrary.ui.theme.LionLayout

@Composable
fun MediaCard(
    mediaItem: MediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onToggleWatchlist: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    Surface(
        modifier =
            modifier
                .width(LionLayout.PosterWidth)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        // Only artwork is rounded: rounding the caption cuts the year at the bottom corners.
        shape = androidx.compose.ui.graphics.RectangleShape,
        color = Color.Transparent,
    ) {
        Column {
            Box(
                Modifier.fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(LionLayout.CardRadius))
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
                    IconButton(onClick = toggle, modifier = Modifier.align(Alignment.TopEnd)) {
                        Icon(
                            if (mediaItem.isWatchlisted) Icons.Filled.Bookmark
                            else Icons.Outlined.BookmarkBorder,
                            modifier =
                                Modifier.size(LionLayout.BookmarkBadge)
                                    .background(
                                        MaterialTheme.colorScheme.scrim.copy(alpha = .65f),
                                        androidx.compose.foundation.shape.CircleShape,
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
