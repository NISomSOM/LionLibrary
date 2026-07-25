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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.singam.lionlibrary.domain.model.Episode
import java.io.File

@Composable
fun EpisodeCard(
    episode: Episode,
    isWatched: Boolean,
    progress: Float?,
    onMarkWatched: (Long) -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .background(Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Render top row: Thumbnail, text, and checkmark
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            // Render thumbnail on the left
            Box(
                modifier = Modifier
                    .width(130.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(8.dp))
            ) {
                if (episode.thumbnailPath != null) {
                    AsyncImage(
                        model = File(episode.thumbnailPath),
                        contentDescription = "Thumbnail",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(Color.DarkGray))
                }
                
                // Render play icon overlay
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 8.dp, bottom = 8.dp)
                        .size(20.dp)
                )

                // Render progress bar overlay
                val progressValue = if (isWatched) 1f else (progress ?: 0f)
                if (progressValue > 0f) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth(progressValue)
                            .height(4.dp)
                            .background(com.singam.lionlibrary.ui.theme.OrangeAccent)
                    )
                }
            }

            // Render text and action icon
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    // Render episode title
                    Text(
                        text = episode.title ?: "Episode ${episode.episodeNumber}",
                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    // Render episode subtitle
                    val subtitleParts = mutableListOf<String>()
                    subtitleParts.add("S${episode.seasonNumber} E${episode.episodeNumber}")
                    if (!episode.airDate.isNullOrBlank()) {
                        subtitleParts.add(episode.airDate)
                    }
                    if (episode.runtime != null && episode.runtime > 0) {
                        subtitleParts.add("${episode.runtime}m")
                    }
                    Text(
                        text = subtitleParts.joinToString(" · "),
                        style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                        color = Color.Gray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Render watched status button
                IconButton(
                    onClick = { onMarkWatched(episode.id) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (isWatched) Icons.Rounded.CheckCircle else Icons.Outlined.CheckCircle,
                        contentDescription = if (isWatched) "Mark as Unwatched" else "Mark as Watched",
                        tint = if (isWatched) Color.White else Color.Gray,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // Render overview text below the top row
        if (!episode.overview.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = episode.overview,
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                color = Color.Gray,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        androidx.compose.material3.HorizontalDivider(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF333333),
            thickness = 1.dp
        )
    }
}
