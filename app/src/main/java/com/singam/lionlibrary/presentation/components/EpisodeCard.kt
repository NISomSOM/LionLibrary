package com.singam.lionlibrary.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.singam.lionlibrary.R
import com.singam.lionlibrary.domain.model.Episode
import com.singam.lionlibrary.ui.theme.LionLayout
import com.singam.lionlibrary.ui.theme.LionTheme
import com.singam.lionlibrary.ui.theme.OrangeAccent
import com.singam.lionlibrary.ui.theme.cardEdge

@Composable
fun EpisodeCard(
    episode: Episode,
    isWatched: Boolean,
    progress: Float?,
    onMarkWatched: (Long) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: () -> Unit = {},
    fallbackArtwork: List<String?> = emptyList(),
) {
    val haptics = LocalHapticFeedback.current
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .background(Color.Transparent)
                .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Box(
                modifier =
                    Modifier.width(96.dp)
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(LionLayout.CardRadius))
                        .cardEdge(LionLayout.CardRadius, LionTheme.colors)
            ) {
                LocalArtwork(
                    listOf(episode.thumbnailPath) + fallbackArtwork,
                    episode.title,
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                )

                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = stringResource(R.string.play),
                    tint = LionTheme.colors.onMedia,
                    modifier =
                        Modifier.align(Alignment.BottomStart)
                            .padding(start = 8.dp, bottom = 8.dp)
                            .size(20.dp),
                )

                val progressValue = if (isWatched) 1f else (progress ?: 0f)
                if (progressValue > 0f) {
                    Box(
                        modifier =
                            Modifier.align(Alignment.BottomStart)
                                .fillMaxWidth(progressValue)
                                .height(4.dp)
                                .background(OrangeAccent)
                    )
                }
            }

            Row(
                modifier = Modifier.weight(1f).padding(start = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = episode.title ?: "Episode ${episode.episodeNumber}",
                        style =
                            MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = LionTheme.colors.onMedia,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    val subtitleParts = mutableListOf<String>()
                    subtitleParts.add(
                        if (episode.episodeEndNumber != null)
                            stringResource(
                                R.string.episode_range,
                                episode.seasonNumber,
                                episode.episodeNumber,
                                episode.episodeEndNumber,
                            )
                        else
                            stringResource(
                                R.string.episode_number,
                                episode.seasonNumber,
                                episode.episodeNumber,
                            )
                    )
                    if (!episode.airDate.isNullOrBlank()) {
                        subtitleParts.add(episode.airDate)
                    }
                    if (episode.runtime != null && episode.runtime > 0) {
                        subtitleParts.add("${episode.runtime}m")
                    }
                    Text(
                        text = subtitleParts.joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium,
                        color = LionTheme.colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                IconButton(onClick = onLongClick) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.more_actions),
                    )
                }
                IconButton(
                    onClick = {
                        haptics.performHapticFeedback(
                            if (isWatched) HapticFeedbackType.ToggleOff
                            else HapticFeedbackType.ToggleOn
                        )
                        onMarkWatched(episode.id)
                    },
                    modifier = Modifier,
                ) {
                    Icon(
                        imageVector =
                            if (isWatched) Icons.Rounded.CheckCircle
                            else Icons.Outlined.CheckCircle,
                        contentDescription =
                            if (isWatched) stringResource(R.string.ui_mark_as_unwatched)
                            else stringResource(R.string.ui_mark_as_watched),
                        tint =
                            if (isWatched) LionTheme.colors.onMedia
                            else LionTheme.colors.textSecondary,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }

        if (!episode.overview.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = episode.overview,
                style = MaterialTheme.typography.bodyMedium,
                color = LionTheme.colors.textSecondary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(),
            color = LionTheme.colors.divider,
            thickness = 1.dp,
        )
    }
}
