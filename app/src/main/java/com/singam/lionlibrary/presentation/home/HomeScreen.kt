package com.singam.lionlibrary.presentation.home

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.singam.lionlibrary.domain.model.MediaItem
import com.singam.lionlibrary.domain.model.MediaType
import com.singam.lionlibrary.domain.model.JumpBackInItem
import com.singam.lionlibrary.presentation.components.JumpBackInCard
import com.singam.lionlibrary.presentation.components.MediaCard
import org.koin.androidx.compose.koinViewModel
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.draw.clip
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass

@androidx.compose.foundation.ExperimentalFoundationApi
@Composable
fun HomeRoot(
    viewModel: HomeViewModel = koinViewModel(),
    snackbarHostState: SnackbarHostState,
    windowSizeClass: WindowSizeClass,
    onNavigateToMovieDetails: (Long) -> Unit,
    onNavigateToShowDetails: (Long) -> Unit,
    onNavigateToPlayer: (String, Long) -> Unit,
    onNavigateToSearch: (com.singam.lionlibrary.domain.model.MediaFilter) -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current


    LaunchedEffect(viewModel.events) {
        viewModel.events.collect { event ->
            when (event) {
                is HomeEvent.NavigateToMovieDetails -> onNavigateToMovieDetails(event.mediaId)
                is HomeEvent.NavigateToShowDetails -> onNavigateToShowDetails(event.mediaId)
                is HomeEvent.NavigateToPlayer -> onNavigateToPlayer(event.mediaType, event.mediaId)
                is HomeEvent.ShowError -> snackbarHostState.showSnackbar(event.message)
                is HomeEvent.LaunchPlayer -> {
                    try {
                        context.startActivity(event.intent)
                    } catch (e: Exception) {
                        snackbarHostState.showSnackbar("Failed to launch player")
                    }
                }
                is HomeEvent.NavigateToSearch -> onNavigateToSearch(event.filter)
            }
        }
    }



    HomeScreen(
        state = state,
        windowSizeClass = windowSizeClass,
        onAction = viewModel::onAction
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@androidx.compose.foundation.ExperimentalFoundationApi
@Composable
fun HomeScreen(
    state: HomeState,
    windowSizeClass: WindowSizeClass,
    onAction: (HomeAction) -> Unit
) {
    var selectedJumpBackInItem by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<JumpBackInItem?>(null) }
    val sheetState = rememberModalBottomSheetState()

    if (state.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (state.carouselItems.isEmpty() && 
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
        val heroItems = state.carouselItems
        if (heroItems.isNotEmpty()) {
            item {
                HeroBannerCarousel(
                    mediaItems = heroItems,
                    windowSizeClass = windowSizeClass,
                    onPlayClick = { id, type -> onAction(HomeAction.OnPlayClick(id, type)) },
                    onInfoClick = { id, type -> onAction(HomeAction.OnMediaClick(id, type)) }
                )
            }
        } else {
            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        if (state.jumpBackInItems.isNotEmpty()) {
            item {
                JumpBackInRow(
                    title = "Jump Back In",
                    items = state.jumpBackInItems,
                    onItemClick = { onAction(HomeAction.OnJumpBackInClick(it)) },
                    onItemLongClick = { selectedJumpBackInItem = it }
                )
            }
        }

        if (state.movies.isNotEmpty()) {
            item {
                MediaRow(
                    title = "Movies",
                    items = state.movies,
                    onItemClick = { onAction(HomeAction.OnMediaClick(it.id, it.mediaType)) },
                    onHeaderClick = { onAction(HomeAction.OnHeaderClick(com.singam.lionlibrary.domain.model.MediaFilter.MOVIES)) }
                )
            }
        }

        if (state.tvShows.isNotEmpty()) {
            item {
                MediaRow(
                    title = "TV Shows",
                    items = state.tvShows,
                    onItemClick = { onAction(HomeAction.OnMediaClick(it.id, it.mediaType)) },
                    onHeaderClick = { onAction(HomeAction.OnHeaderClick(com.singam.lionlibrary.domain.model.MediaFilter.TV_SHOWS)) }
                )
            }
        }

        if (state.anime.isNotEmpty()) {
            item {
                MediaRow(
                    title = "Anime",
                    items = state.anime,
                    onItemClick = { onAction(HomeAction.OnMediaClick(it.id, it.mediaType)) },
                    onHeaderClick = { onAction(HomeAction.OnHeaderClick(com.singam.lionlibrary.domain.model.MediaFilter.ANIME)) }
                )
            }
        }

        if (state.recentlyAdded.isNotEmpty()) {
            item {
                MediaRow(
                    title = "Recently Added",
                    items = state.recentlyAdded,
                    onItemClick = { onAction(HomeAction.OnMediaClick(it.id, it.mediaType)) }
                )
            }
        }

        state.genresContent.forEach { (genre, items) ->
            if (items.isNotEmpty()) {
                item {
                    MediaRow(
                        title = genre,
                        items = items,
                        onItemClick = { onAction(HomeAction.OnMediaClick(it.id, it.mediaType)) }
                    )
                }
            }
        }
    }

    selectedJumpBackInItem?.let { item ->
        ModalBottomSheet(
            onDismissRequest = { selectedJumpBackInItem = null },
            sheetState = sheetState,
            containerColor = Color(0xFF1E1E1E)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val imagePath = if (item.mediaType == MediaType.MOVIE) {
                        item.posterPath ?: item.backdropPath
                    } else {
                        item.thumbnailPath ?: item.backdropPath ?: item.posterPath
                    }
                    if (imagePath != null) {
                        AsyncImage(
                            model = File(imagePath),
                            contentDescription = item.mediaTitle,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .width(125.dp)
                                .height(70.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = item.mediaTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        if (item.mediaType != MediaType.MOVIE) {
                            Text(
                                text = "S${item.seasonNumber}E${item.episodeNumber} • ${item.episodeTitle ?: ""}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Gray
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                JumpBackInOptionItem(
                    icon = Icons.Default.Info,
                    text = "Go to details",
                    onClick = {
                        selectedJumpBackInItem = null
                        onAction(HomeAction.OnMediaClick(item.mediaId, item.mediaType))
                    }
                )
                JumpBackInOptionItem(
                    icon = Icons.Default.PlayArrow,
                    text = "Play in external player",
                    onClick = {
                        selectedJumpBackInItem = null
                        onAction(HomeAction.OnPlayExternal(item))
                    },
                    iconTint = com.singam.lionlibrary.ui.theme.OrangeAccent
                )
                JumpBackInOptionItem(
                    icon = Icons.Default.Refresh,
                    text = "Start from beginning",
                    onClick = {
                        selectedJumpBackInItem = null
                        onAction(HomeAction.OnStartFromBeginning(item))
                    },
                    iconTint = com.singam.lionlibrary.ui.theme.OrangeAccent
                )
                JumpBackInOptionItem(
                    icon = Icons.Default.Delete,
                    text = "Remove",
                    onClick = {
                        selectedJumpBackInItem = null
                        onAction(HomeAction.OnRemoveWatchProgress(item))
                    },
                    iconTint = com.singam.lionlibrary.ui.theme.OrangeAccent
                )
            }
        }
    }
}

@Composable
private fun JumpBackInOptionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    onClick: () -> Unit,
    iconTint: Color = Color.White
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = iconTint,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(24.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
    }
}

// Hero carousel constants.
private const val HERO_CONTENT_PARALLAX = 0.70f
private const val HERO_AUTO_SCROLL_MS = 8_000L
private const val HERO_VIEWPORT_RATIO = 0.65f
private val HERO_MIN_HEIGHT = 200.dp
private val HERO_MAX_HEIGHT = 560.dp

@androidx.compose.foundation.ExperimentalFoundationApi
@Composable
fun HeroBannerCarousel(
    mediaItems: List<MediaItem>,
    windowSizeClass: WindowSizeClass,
    onPlayClick: (Long, MediaType) -> Unit,
    onInfoClick: (Long, MediaType) -> Unit
) {
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { mediaItems.size })
    val coroutineScope = rememberCoroutineScope()

    // Auto-scroll timer.
    val autoScrollPage = pagerState.currentPage
    LaunchedEffect(autoScrollPage, mediaItems.size) {
        if (mediaItems.size <= 1) return@LaunchedEffect
        delay(HERO_AUTO_SCROLL_MS)
        while (pagerState.isScrollInProgress) {
            delay(100L)
        }
        val nextPage = (pagerState.currentPage + 1) % mediaItems.size
        coroutineScope.launch {
            pagerState.animateScrollToPage(nextPage)
        }
    }

    // Calculate hero height.
    val configuration = LocalConfiguration.current
    val heroHeight = (configuration.screenHeightDp.dp * HERO_VIEWPORT_RATIO).coerceIn(HERO_MIN_HEIGHT, HERO_MAX_HEIGHT)
    val heroWidthPx = with(LocalDensity.current) { configuration.screenWidthDp.dp.toPx() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(heroHeight)
            .clip(RoundedCornerShape(0.dp))
    ) {
        androidx.compose.foundation.pager.HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val mediaItem = mediaItems[page]
                val imagePath = mediaItem.backdropPath ?: mediaItem.posterPath

                // Parallax offset.
                val signedOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                val pageOffset = kotlin.math.abs(signedOffset)
                val pageAlpha = 1f - pageOffset.coerceIn(0f, 1f)

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = pageAlpha
                        }
                ) {
                    // Background image.
                    if (imagePath != null) {
                        AsyncImage(
                            model = File(imagePath),
                            contentDescription = mediaItem.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Gradient overlay.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colorStops = arrayOf(
                                        0.00f to Color.Transparent,
                                        0.30f to Color.Black.copy(alpha = 0.05f),
                                        0.50f to Color.Black.copy(alpha = 0.15f),
                                        0.65f to Color.Black.copy(alpha = 0.35f),
                                        0.75f to Color.Black.copy(alpha = 0.55f),
                                        0.85f to Color.Black.copy(alpha = 0.75f),
                                        0.92f to Color.Black.copy(alpha = 0.92f),
                                        1.00f to Color.Black
                                    )
                                )
                            )
                    )

                    // Content layer.
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 110.dp, start = 24.dp, end = 24.dp)
                            .graphicsLayer {
                                translationX = -signedOffset * heroWidthPx * HERO_CONTENT_PARALLAX
                                val scale = 1f - (pageOffset * 0.15f).coerceIn(0f, 0.15f)
                                scaleX = scale
                                scaleY = scale
                            },
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
                            if (mediaItem.year != null) append(mediaItem.year).append(" \u2022 ")
                            if (mediaItem.genres != null) append(mediaItem.genres.split(",").take(2).joinToString(", ")).append(" \u2022 ")
                            if (mediaItem.rating != null) append(mediaItem.rating).append(" \u2605")
                        }.trimEnd(' ', '\u2022')

                        if (subtitleInfo.isNotBlank()) {
                            Text(
                                text = subtitleInfo,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                                color = Color.White.copy(alpha = 0.9f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Bottom controls.
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
                    shape = RoundedCornerShape(24.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                    modifier = Modifier.fillMaxWidth(0.6f)
                ) {
                    Text("View Details", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }

                // Pill indicators.
                if (mediaItems.size > 1) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        repeat(mediaItems.size) { index ->
                            val isActive = pagerState.currentPage == index
                            val pillWidth by androidx.compose.animation.core.animateDpAsState(
                                targetValue = if (isActive) 24.dp else 8.dp,
                                animationSpec = tween(durationMillis = 300),
                                label = "pillWidth"
                            )
                            val pillAlpha by androidx.compose.animation.core.animateFloatAsState(
                                targetValue = if (isActive) 1f else 0.4f,
                                animationSpec = tween(durationMillis = 300),
                                label = "pillAlpha"
                            )
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 3.dp)
                                    .width(pillWidth)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Color.White.copy(alpha = pillAlpha))
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
    onItemClick: (MediaItem) -> Unit,
    onHeaderClick: (() -> Unit)? = null
) {
    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .then(if (onHeaderClick != null) Modifier.clickable(onClick = onHeaderClick) else Modifier)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(3.dp)
                    .background(com.singam.lionlibrary.ui.theme.OrangeAccent, androidx.compose.foundation.shape.RoundedCornerShape(1.5.dp))
            )
        }
        
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
    onItemClick: (JumpBackInItem) -> Unit,
    onItemLongClick: (JumpBackInItem) -> Unit = {}
) {
    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(3.dp)
                    .background(com.singam.lionlibrary.ui.theme.OrangeAccent, androidx.compose.foundation.shape.RoundedCornerShape(1.5.dp))
            )
        }
        
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items, key = { "${it.mediaId}-${it.episodeId}" }) { item ->
                JumpBackInCard(
                    item = item,
                    onClick = { onItemClick(item) },
                    onLongClick = { onItemLongClick(item) }
                )
            }
        }
    }
}
