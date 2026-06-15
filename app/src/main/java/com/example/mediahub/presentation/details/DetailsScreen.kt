package com.example.mediahub.presentation.details

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.mediahub.domain.model.MediaType
import com.example.mediahub.presentation.components.EpisodeCard
import org.koin.androidx.compose.koinViewModel
import java.io.File

@Composable
fun DetailsRoot(
    snackbarHostState: SnackbarHostState,
    viewModel: DetailsViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(viewModel.events) {
        viewModel.events.collect { event ->
            when (event) {
                is DetailsEvent.LaunchPlayer -> {
                    try {
                        context.startActivity(event.intent)
                    } catch (e: Exception) {
                        snackbarHostState.showSnackbar("Failed to launch player: ${e.message}")
                    }
                }
                is DetailsEvent.ShowError -> {
                    snackbarHostState.showSnackbar(event.message)
                }
            }
        }
    }



    DetailsScreen(
        state = state,
        onAction = viewModel::onAction
    )
}

@Composable
fun DetailsScreen(
    state: DetailsState,
    onAction: (DetailsAction) -> Unit
) {
    if (state.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val media = state.media ?: return

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        // Hero Header (Backdrop, Poster, Title, Meta)
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
            ) {
                // Backdrop Image
                if (media.backdropPath != null) {
                    AsyncImage(
                        model = File(media.backdropPath),
                        contentDescription = "Backdrop",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (media.posterPath != null) {
                    AsyncImage(
                        model = File(media.posterPath),
                        contentDescription = "Poster",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                }
                
                // Gradient Overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.4f), MaterialTheme.colorScheme.background),
                                startY = 0f,
                                endY = Float.POSITIVE_INFINITY
                            )
                        )
                )
                
                // Show Logo or Text Title Overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 8.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    if (media.logoPath != null) {
                        AsyncImage(
                            model = File(media.logoPath),
                            contentDescription = "Show Logo",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth(0.6f)
                                .height(100.dp)
                        )
                    } else {
                        Text(
                            text = media.title,
                            style = MaterialTheme.typography.headlineLarge,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }

            // Info Section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Genres
                if (!media.genres.isNullOrBlank()) {
                    val genresText = media.genres.split(",").take(3).joinToString(" • ") { it.trim() }
                    Text(
                        text = genresText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Play / Resume Button
                Button(
                    onClick = {
                        if (media.mediaType == MediaType.MOVIE) onAction(DetailsAction.OnPlayMovie)
                        else onAction(DetailsAction.OnResumeTvShow)
                    },
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    )
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Play")
                    Spacer(modifier = Modifier.width(8.dp))
                    val buttonText = if (media.mediaType == MediaType.MOVIE) {
                        if (state.isMovieWatched) "Play Again" else "Play"
                    } else {
                        val nextEp = state.nextEpisodeToWatch
                        if (nextEp != null) "Play S${nextEp.seasonNumber}E${nextEp.episodeNumber}" else "Play"
                    }
                    Text(buttonText, style = MaterialTheme.typography.titleMedium)
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Metadata (Year, Rating)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (media.year != null) {
                        Text(
                            text = media.year.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (media.rating != null && media.rating > 0.0) {
                        Text(
                            text = "IMDb ${media.rating}",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color(0xFFE5B13A)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Overview
                if (!media.overview.isNullOrBlank()) {
                    Text(
                        text = media.overview,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        // TV Show and Anime Season Selector
        if ((media.mediaType == MediaType.TV_SHOW || media.mediaType == MediaType.ANIME) && state.seasons.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.seasons.forEach { season ->
                        FilterChip(
                            selected = state.selectedSeasonNumber == season.seasonNumber,
                            onClick = { onAction(DetailsAction.OnSeasonSelected(season.seasonNumber)) },
                            label = { Text("Season ${season.seasonNumber}") }
                        )
                    }
                }
            }
        }

        // Episodes List
        if (state.episodes.isNotEmpty()) {
            item {
                Text(
                    text = "Episodes",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(16.dp)
                )
            }
            
            val currentSeason = state.seasons.find { it.seasonNumber == state.selectedSeasonNumber }
            items(state.episodes, key = { it.id }) { episode ->
                EpisodeCard(
                    episode = episode,
                    isWatched = state.watchedEpisodeIds.contains(episode.id),
                    seasonPosterPath = currentSeason?.posterPath,
                    showBackdropPath = media.backdropPath,
                    showPosterPath = media.posterPath,
                    onClick = { onAction(DetailsAction.OnPlayEpisode(episode.id, episode.filePath)) },
                    onToggleWatched = { onAction(DetailsAction.OnMarkEpisodeWatchedToggle(episode.id)) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        } else if ((media.mediaType == MediaType.TV_SHOW || media.mediaType == MediaType.ANIME) && state.seasons.isNotEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No episodes found for this season.")
                }
            }
        }
    }
}
