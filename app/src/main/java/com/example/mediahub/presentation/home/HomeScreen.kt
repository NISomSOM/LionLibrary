package com.example.mediahub.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.mediahub.domain.model.MediaItem
import com.example.mediahub.domain.model.MediaType
import com.example.mediahub.domain.model.JumpBackInItem
import com.example.mediahub.presentation.components.JumpBackInCard
import com.example.mediahub.presentation.components.MediaCard
import org.koin.androidx.compose.koinViewModel
import androidx.compose.ui.platform.LocalContext
import java.io.File

@androidx.compose.foundation.ExperimentalFoundationApi
@Composable
fun HomeRoot(
    viewModel: HomeViewModel = koinViewModel(),
    snackbarHostState: SnackbarHostState,
    onNavigateToMovieDetails: (Long) -> Unit,
    onNavigateToShowDetails: (Long) -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current


    LaunchedEffect(viewModel.events) {
        viewModel.events.collect { event ->
            when (event) {
                is HomeEvent.NavigateToMovieDetails -> onNavigateToMovieDetails(event.mediaId)
                is HomeEvent.NavigateToShowDetails -> onNavigateToShowDetails(event.mediaId)
                is HomeEvent.ShowError -> snackbarHostState.showSnackbar(event.message)
                is HomeEvent.LaunchPlayer -> {
                    try {
                        context.startActivity(event.intent)
                    } catch (e: Exception) {
                        snackbarHostState.showSnackbar("Failed to launch player")
                    }
                }

            }
        }
    }



    HomeScreen(
        state = state,
        onAction = viewModel::onAction
    )
}

@androidx.compose.foundation.ExperimentalFoundationApi
@Composable
fun HomeScreen(
    state: HomeState,
    onAction: (HomeAction) -> Unit
) {
    if (state.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (state.featuredItem == null && 
        state.jumpBackInItems.isEmpty() && 
        state.movies.isEmpty() && 
        state.tvShows.isEmpty() && 
        state.anime.isEmpty() && 
        state.recentlyAdded.isEmpty()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Your library is empty. Go to to Settings to add your media folders and scan.",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        // Hero Banner
        val heroItems = state.recentlyAdded.take(10)
        if (heroItems.isNotEmpty()) {
            item {
                HeroBannerCarousel(
                    mediaItems = heroItems,
                    onPlayClick = { id, type -> onAction(HomeAction.OnPlayClick(id, type)) },
                    onInfoClick = { id, type -> onAction(HomeAction.OnMediaClick(id, type)) }
                )
            }
        } else if (state.featuredItem != null) {
            item {
                HeroBannerCarousel(
                    mediaItems = listOf(state.featuredItem),
                    onPlayClick = { id, type -> onAction(HomeAction.OnPlayClick(id, type)) },
                    onInfoClick = { id, type -> onAction(HomeAction.OnMediaClick(id, type)) }
                )
            }
        } else {
            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        // Jump Back In
        if (state.jumpBackInItems.isNotEmpty()) {
            item {
                JumpBackInRow(
                    title = "Jump Back In",
                    items = state.jumpBackInItems,
                    onItemClick = { onAction(HomeAction.OnJumpBackInClick(it.filePath ?: "")) }
                )
            }
        }

        // Movies
        if (state.movies.isNotEmpty()) {
            item {
                MediaRow(
                    title = "Movies",
                    items = state.movies,
                    onItemClick = { onAction(HomeAction.OnMediaClick(it.id, MediaType.MOVIE)) }
                )
            }
        }

        // TV Shows
        if (state.tvShows.isNotEmpty()) {
            item {
                MediaRow(
                    title = "TV Shows",
                    items = state.tvShows,
                    onItemClick = { onAction(HomeAction.OnMediaClick(it.id, MediaType.TV_SHOW)) }
                )
            }
        }

        // Anime
        if (state.anime.isNotEmpty()) {
            item {
                MediaRow(
                    title = "Anime",
                    items = state.anime,
                    onItemClick = { onAction(HomeAction.OnMediaClick(it.id, MediaType.ANIME)) }
                )
            }
        }

        // Recently Added
        if (state.recentlyAdded.isNotEmpty()) {
            item {
                MediaRow(
                    title = "Recently Added",
                    items = state.recentlyAdded,
                    onItemClick = { onAction(HomeAction.OnMediaClick(it.id, it.mediaType)) }
                )
            }
        }
    }
}

@androidx.compose.foundation.ExperimentalFoundationApi
@Composable
fun HeroBannerCarousel(
    mediaItems: List<MediaItem>,
    onPlayClick: (Long, MediaType) -> Unit,
    onInfoClick: (Long, MediaType) -> Unit
) {
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { mediaItems.size })

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(500.dp)
    ) {
        androidx.compose.foundation.pager.HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val mediaItem = mediaItems[page]
            val imagePath = mediaItem.backdropPath ?: mediaItem.posterPath
            
            val pageOffset = (
                (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
            ).let { kotlin.math.abs(it) }
            val pageAlpha = 1f - pageOffset.coerceIn(0f, 1f)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = pageAlpha
                    }
            ) {
                if (imagePath != null) {
                    AsyncImage(
                        model = File(imagePath),
                        contentDescription = mediaItem.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Gradient overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
                                    MaterialTheme.colorScheme.background
                                ),
                                startY = 100f
                            )
                        )
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 110.dp, start = 24.dp, end = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (mediaItem.logoPath != null) {
                        AsyncImage(
                            model = File(mediaItem.logoPath),
                            contentDescription = "Show Logo",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth(0.6f)
                                .height(80.dp)
                                .padding(bottom = 4.dp)
                        )
                    } else {
                        Text(
                            text = mediaItem.title,
                            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    val subtitleInfo = buildString {
                        if (mediaItem.year != null) append(mediaItem.year).append(" • ")
                        if (mediaItem.genres != null) append(mediaItem.genres.split(",").take(2).joinToString(", ")).append(" • ")
                        if (mediaItem.rating != null) append(mediaItem.rating).append(" ★")
                    }.trimEnd(' ', '•')
                    
                    if (subtitleInfo.isNotBlank()) {
                        Text(
                            text = subtitleInfo,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }

        // Stationary elements (Buttons and Pagination)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val currentItem = mediaItems[pagerState.currentPage]

            Button(
                onClick = { onInfoClick(currentItem.id, currentItem.mediaType) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black
                ),
                modifier = Modifier.fillMaxWidth(0.5f)
            ) {
                Icon(imageVector = Icons.Default.Info, contentDescription = "Info")
                Spacer(modifier = Modifier.width(4.dp))
                Text("Details", fontWeight = FontWeight.Bold)
            }
            
            if (mediaItems.size > 1) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    repeat(mediaItems.size) { iteration ->
                        val color = if (pagerState.currentPage == iteration) Color.White else Color.White.copy(alpha = 0.5f)
                        val width = if (pagerState.currentPage == iteration) 24.dp else 8.dp
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .width(width)
                                .height(8.dp)
                                .background(color, androidx.compose.foundation.shape.CircleShape)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MediaRow(
    title: String,
    items: List<MediaItem>,
    onItemClick: (MediaItem) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items, key = { it.id }) { item ->
                MediaCard(
                    mediaItem = item,
                    onClick = { onItemClick(item) }
                )
            }
        }
    }
}

@Composable
fun JumpBackInRow(
    title: String,
    items: List<JumpBackInItem>,
    onItemClick: (JumpBackInItem) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items, key = { "${it.mediaId}-${it.episodeId}" }) { item ->
                JumpBackInCard(
                    item = item,
                    onClick = { onItemClick(item) }
                )
            }
        }
    }
}
