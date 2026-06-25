package com.singam.lionlibrary.presentation.details

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.Tab
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.alpha
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.singam.lionlibrary.domain.model.MediaType
import com.singam.lionlibrary.presentation.components.EpisodeCard
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

@OptIn(ExperimentalMaterial3Api::class)
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
    val listState = rememberLazyListState()
    
    val showTopBarBackground by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 }
    }
    val topBarColor by animateColorAsState(
        targetValue = if (showTopBarBackground) Color(0xFF141414) else Color.Transparent,
        label = "topBarColor"
    )
    val logoAlpha by animateFloatAsState(
        targetValue = if (showTopBarBackground) 1f else 0f,
        label = "logoAlpha"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
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
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
                        
                        // Genres
                        if (!media.genres.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            val genresText = media.genres.split(",").take(3).joinToString(" • ") { it.trim() }
                            Text(
                                text = genresText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.LightGray
                            )
                        }
                    }
                }
            }

            // Info Section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Play / Resume Button
                Button(
                    onClick = {
                        if (media.mediaType == MediaType.MOVIE) onAction(DetailsAction.OnPlayMovie)
                        else onAction(DetailsAction.OnResumeTvShow)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(100),
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
                    Text(buttonText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Metadata (Year, Duration, Rating Badge, IMDb)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Year and Duration
                    val yearText = media.year?.toString() ?: ""
                    val durationText = media.duration?.let { dur ->
                        val hours = dur / 60
                        val mins = dur % 60
                        if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
                    } ?: ""
                    
                    val combinedText = if (yearText.isNotEmpty() && durationText.isNotEmpty()) "$yearText - $durationText" 
                                       else if (yearText.isNotEmpty()) yearText 
                                       else durationText

                    if (combinedText.isNotEmpty()) {
                        Text(
                            text = combinedText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    // TV Rating Badge
                    if (!media.certification.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .border(1.dp, Color.Gray, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = media.certification,
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.LightGray
                            )
                        }
                    }

                    // IMDb rating
                    if (media.rating != null && media.rating > 0.0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "IMDb",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.Gray
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = String.format("%.1f", media.rating),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE5B13A)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Overview
                if (!media.overview.isNullOrBlank()) {
                    var isExpanded by remember { mutableStateOf(false) }
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = media.overview,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White,
                            maxLines = if (isExpanded) Int.MAX_VALUE else 4,
                            overflow = TextOverflow.Ellipsis
                        )
                        
                        Text(
                            text = if (isExpanded) "Show Less ▴" else "Show More ▾",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray,
                            modifier = Modifier
                                .clickable { isExpanded = !isExpanded }
                                .padding(vertical = 8.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // TV Show and Anime Season Selector
        if ((media.mediaType == MediaType.TV_SHOW || media.mediaType == MediaType.ANIME) && state.seasons.isNotEmpty()) {
            val selectedSeasonIndex = state.seasons.indexOfFirst { it.seasonNumber == state.selectedSeasonNumber }.coerceAtLeast(0)
            
            item {
                Box(modifier = Modifier.background(Color(0xFF141414))) {
                    @Suppress("DEPRECATION")
                    ScrollableTabRow(
                        selectedTabIndex = selectedSeasonIndex,
                        edgePadding = 16.dp,
                        indicator = { tabPositions ->
                            if (selectedSeasonIndex < tabPositions.size) {
                                TabRowDefaults.Indicator(
                                    modifier = Modifier.tabIndicatorOffset(
                                        tabPositions[selectedSeasonIndex]
                                    ),
                                    color = Color.White
                                )
                            }
                        },
                        containerColor = Color.Transparent,
                        divider = {}
                    ) {
                        state.seasons.forEachIndexed { index, season ->
                            Tab(
                                selected = index == selectedSeasonIndex,
                                onClick = { onAction(DetailsAction.OnSeasonSelected(season.seasonNumber)) },
                                text = {
                                    Text(
                                        text = season.name ?: "Season ${season.seasonNumber}",
                                        fontWeight = if (index == selectedSeasonIndex) 
                                            FontWeight.Bold else FontWeight.Normal,
                                        color = if (index == selectedSeasonIndex) 
                                            Color.White else Color.Gray
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }

        // Episodes List
        if (state.episodes.isNotEmpty()) {
            
            items(
                items = state.episodes,
                key = { it.id }
            ) { episode ->
                EpisodeCard(
                    episode = episode,
                    isWatched = state.watchedEpisodeIds.contains(episode.id),
                    onMarkWatched = { onAction(DetailsAction.OnMarkEpisodeWatchedToggle(it)) },
                    modifier = Modifier.padding(horizontal = 0.dp, vertical = 0.dp)
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

        // TopAppBar overlay
        TopAppBar(
            title = {
                Box(modifier = Modifier.fillMaxWidth().padding(end = 48.dp).alpha(logoAlpha), contentAlignment = Alignment.Center) {
                    if (media.logoPath != null) {
                        AsyncImage(
                            model = File(media.logoPath),
                            contentDescription = "Show Logo",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.height(40.dp)
                        )
                    } else {
                        Text(media.title, color = Color.White)
                    }
                }
            },
            navigationIcon = {
                val dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
                IconButton(onClick = { dispatcher?.onBackPressed() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = topBarColor,
                titleContentColor = Color.White
            )
        )
    }
}

