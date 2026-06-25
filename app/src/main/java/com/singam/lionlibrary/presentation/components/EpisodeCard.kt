package com.singam.lionlibrary.presentation.components

import androidx.compose.foundation.background
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
    onMarkWatched: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Top Row: Thumbnail + Title/Subtitle + Check Icon
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            // Left: Thumbnail
            Box(
                modifier = Modifier
                    .width(110.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(4.dp))
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
                
                // Play Icon Overlay (bottom-left)
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp)
                        .size(24.dp)
                )
            }

            // Middle: Title & Subtitle
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                // Title
                Text(
                    text = episode.title ?: "Episode ${episode.episodeNumber}",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Subtitle
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
                    fontSize = 12.sp,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Right: Watched Button
            IconButton(
                onClick = { onMarkWatched(episode.id) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (isWatched) Icons.Rounded.CheckCircle else Icons.Outlined.CheckCircle,
                    contentDescription = if (isWatched) "Mark as Unwatched" else "Mark as Watched",
                    tint = if (isWatched) Color.White else Color.Gray
                )
            }
        }

        // Overview below Top Row
        if (!episode.overview.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = episode.overview,
                fontSize = 13.sp,
                color = Color.Gray,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 18.sp
            )
        }
    }
}
