package com.singam.lionlibrary.presentation.home

import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.singam.lionlibrary.domain.model.JumpBackInItem
import com.singam.lionlibrary.domain.model.MediaItem
import com.singam.lionlibrary.domain.model.MediaType
import com.singam.lionlibrary.presentation.components.ArtworkTitle
import com.singam.lionlibrary.presentation.components.JumpBackInCard
import com.singam.lionlibrary.presentation.components.LocalArtwork
import com.singam.lionlibrary.presentation.components.MediaCard
import com.singam.lionlibrary.presentation.components.ShelfHeading
import com.singam.lionlibrary.presentation.navigation.LocalFloatingNavigationInset
import com.singam.lionlibrary.ui.theme.LionLayout
import com.singam.lionlibrary.ui.theme.LionMotion
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@androidx.compose.foundation.ExperimentalFoundationApi
@Composable
fun HomeRoot(
    viewModel: HomeViewModel = koinViewModel(),
    snackbarHostState: SnackbarHostState,
    windowSizeClass: WindowSizeClass,
    onNavigateToMovieDetails: (Long) -> Unit,
    onNavigateToShowDetails: (Long) -> Unit,
    onNavigateToPlayer: (String, Long) -> Unit,
    onNavigateToLibrary: (com.singam.lionlibrary.domain.model.MediaFilter) -> Unit,
    onSettingsClick: () -> Unit = {},
    onMediaClick: (Long, MediaType) -> Unit = { _, _ -> },
    onLibraryClick: (com.singam.lionlibrary.domain.model.MediaFilter) -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = androidx.compose.ui.platform.LocalResources.current
    val snackbarScope = rememberCoroutineScope()
    // Snackbars run in their own coroutine so a visible message never delays navigation.
    fun showSnackbar(message: String) {
        snackbarScope.launch { snackbarHostState.showSnackbar(message) }
    }

    LaunchedEffect(viewModel.events) {
        viewModel.events.collect { event ->
            when (event) {
                is HomeEvent.NavigateToPlayer -> onNavigateToPlayer(event.mediaType, event.mediaId)
                is HomeEvent.ShowMessage -> showSnackbar(event.message.resolve(context))
                is HomeEvent.ShowError -> showSnackbar(event.message.resolve(context))
                is HomeEvent.LaunchPlayer -> {
                    try {
                        context.startActivity(event.intent)
                    } catch (e: Exception) {
                        showSnackbar(
                            resources.getString(
                                com.singam.lionlibrary.R.string.external_launch_error
                            )
                        )
                    }
                }
            }
        }
    }

    HomeScreen(
        state = state,
        windowSizeClass = windowSizeClass,
        onAction = viewModel::onAction,
        onSettingsClick = onSettingsClick,
        onMediaClick = { id, type ->
            if (type == MediaType.MOVIE) onNavigateToMovieDetails(id)
            else onNavigateToShowDetails(id)
        },
        onLibraryClick = onNavigateToLibrary,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@androidx.compose.foundation.ExperimentalFoundationApi
@Composable
fun HomeScreen(
    state: HomeState,
    windowSizeClass: WindowSizeClass,
    onAction: (HomeAction) -> Unit,
    onSettingsClick: () -> Unit = {},
    onMediaClick: (Long, MediaType) -> Unit = { _, _ -> },
    onLibraryClick: (com.singam.lionlibrary.domain.model.MediaFilter) -> Unit = {},
) {
    val selectedJumpBackInItem = state.selectedJumpBackInItem
    val sheetState = rememberModalBottomSheetState()
    com.singam.lionlibrary.presentation.components.DeletionDialog(
        state.deletion,
        { onAction(HomeAction.ConfirmDelete) },
        { onAction(HomeAction.DismissDelete) },
    )
    state.selectedMediaForOptions?.let { media ->
        ModalBottomSheet(
            onDismissRequest = { onAction(HomeAction.MediaOptions(null)) },
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                Text(
                    media.title,
                    Modifier.padding(24.dp),
                    style = MaterialTheme.typography.titleMedium,
                )
                JumpBackInOptionItem(
                    Icons.Default.Delete,
                    androidx.compose.ui.res.stringResource(
                        com.singam.lionlibrary.R.string.delete_from_device
                    ),
                    { onAction(HomeAction.RequestDelete(media.id)) },
                    iconTint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }

    if (state.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    state.error?.let { error ->
        Box(
            Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(error.resolve(LocalContext.current), color = MaterialTheme.colorScheme.error)
        }
        return
    }
    if (
        state.carouselItems.isEmpty() &&
            state.jumpBackInItems.isEmpty() &&
            state.movies.isEmpty() &&
            state.tvShows.isEmpty() &&
            state.anime.isEmpty() &&
            state.recentlyAdded.isEmpty()
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text =
                        androidx.compose.ui.res.stringResource(
                            com.singam.lionlibrary.R.string.library_empty_help
                        ),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                )
                Button(onClick = onSettingsClick) {
                    Text(
                        androidx.compose.ui.res.stringResource(
                            com.singam.lionlibrary.R.string.open_settings
                        )
                    )
                }
            }
        }
        return
    }

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    LazyColumn(
        state = listState,
        modifier =
            Modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
        contentPadding = PaddingValues(bottom = LocalFloatingNavigationInset.current + 16.dp),
    ) {
        val heroItems = state.carouselItems
        if (heroItems.isNotEmpty()) {
            item(key = "hero", contentType = "hero") {
                HeroBannerCarousel(
                    mediaItems = heroItems,
                    verticalScrollOffset = {
                        if (listState.firstVisibleItemIndex == 0)
                            listState.firstVisibleItemScrollOffset.toFloat()
                        else 0f
                    },
                    onInfoClick = { id, type -> onMediaClick(id, type) },
                    onItemLongClick = { onAction(HomeAction.MediaOptions(it)) },
                )
            }
        } else {
            item(key = "top-spacer", contentType = "spacer") {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        if (state.jumpBackInItems.isNotEmpty()) {
            item(key = "jump-back-in", contentType = "jump-back-in") {
                JumpBackInRow(
                    title =
                        androidx.compose.ui.res.stringResource(
                            com.singam.lionlibrary.R.string.ui_jump_back_in
                        ),
                    items = state.jumpBackInItems,
                    onItemClick = { onAction(HomeAction.OnJumpBackInClick(it)) },
                    onItemLongClick = { onAction(HomeAction.SelectOptions(it)) },
                )
            }
        }

        if (state.movies.isNotEmpty()) {
            item(key = "movies", contentType = "shelf") {
                MediaRow(
                    title =
                        androidx.compose.ui.res.stringResource(
                            com.singam.lionlibrary.R.string.movies
                        ),
                    items = state.movies,
                    onItemClick = { onMediaClick(it.id, it.mediaType) },
                    onItemLongClick = { onAction(HomeAction.MediaOptions(it)) },
                    onHeaderClick = {
                        onLibraryClick(com.singam.lionlibrary.domain.model.MediaFilter.MOVIES)
                    },
                )
            }
        }

        if (state.tvShows.isNotEmpty()) {
            item(key = "tv-shows", contentType = "shelf") {
                MediaRow(
                    title =
                        androidx.compose.ui.res.stringResource(
                            com.singam.lionlibrary.R.string.tv_shows
                        ),
                    items = state.tvShows,
                    onItemClick = { onMediaClick(it.id, it.mediaType) },
                    onItemLongClick = { onAction(HomeAction.MediaOptions(it)) },
                    onHeaderClick = {
                        onLibraryClick(com.singam.lionlibrary.domain.model.MediaFilter.TV_SHOWS)
                    },
                )
            }
        }

        if (state.anime.isNotEmpty()) {
            item(key = "anime", contentType = "shelf") {
                MediaRow(
                    title =
                        androidx.compose.ui.res.stringResource(
                            com.singam.lionlibrary.R.string.anime
                        ),
                    items = state.anime,
                    onItemClick = { onMediaClick(it.id, it.mediaType) },
                    onItemLongClick = { onAction(HomeAction.MediaOptions(it)) },
                    onHeaderClick = {
                        onLibraryClick(com.singam.lionlibrary.domain.model.MediaFilter.ANIME)
                    },
                )
            }
        }

        if (state.recentlyAdded.isNotEmpty()) {
            item(key = "recently-added", contentType = "shelf") {
                MediaRow(
                    title =
                        androidx.compose.ui.res.stringResource(
                            com.singam.lionlibrary.R.string.ui_recently_added
                        ),
                    items = state.recentlyAdded,
                    onItemClick = { onMediaClick(it.id, it.mediaType) },
                    onItemLongClick = { onAction(HomeAction.MediaOptions(it)) },
                )
            }
        }

        state.genresContent.forEach { (genre, items) ->
            if (items.isNotEmpty()) {
                item(key = "genre:$genre", contentType = "shelf") {
                    MediaRow(
                        title = genre,
                        items = items,
                        onItemClick = { onMediaClick(it.id, it.mediaType) },
                        onItemLongClick = { onAction(HomeAction.MediaOptions(it)) },
                    )
                }
            }
        }
    }

    selectedJumpBackInItem?.let { item ->
        ModalBottomSheet(
            onDismissRequest = { onAction(HomeAction.SelectOptions(null)) },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val imagePath =
                        if (item.mediaType == MediaType.MOVIE) {
                            item.posterPath ?: item.backdropPath
                        } else {
                            item.thumbnailPath ?: item.backdropPath ?: item.posterPath
                        }
                    if (imagePath != null) {
                        LocalArtwork(
                            paths = listOf(imagePath),
                            contentDescription = item.mediaTitle,
                            contentScale = ContentScale.Crop,
                            modifier =
                                Modifier.width(125.dp).height(70.dp).clip(RoundedCornerShape(8.dp)),
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = item.mediaTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                        if (item.mediaType != MediaType.MOVIE) {
                            Text(
                                text =
                                    "S${item.seasonNumber}E${item.episodeNumber} • ${item.episodeTitle ?: ""}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Gray,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                JumpBackInOptionItem(
                    icon = Icons.Default.Info,
                    text =
                        androidx.compose.ui.res.stringResource(
                            com.singam.lionlibrary.R.string.ui_go_to_details
                        ),
                    onClick = {
                        onAction(HomeAction.SelectOptions(null))
                        onMediaClick(item.mediaId, item.mediaType)
                    },
                )
                JumpBackInOptionItem(
                    icon = Icons.Default.PlayArrow,
                    text =
                        androidx.compose.ui.res.stringResource(
                            com.singam.lionlibrary.R.string.ui_play_in_external_player
                        ),
                    onClick = {
                        onAction(HomeAction.SelectOptions(null))
                        onAction(HomeAction.OnPlayExternal(item))
                    },
                    iconTint = MaterialTheme.colorScheme.primary,
                )
                JumpBackInOptionItem(
                    icon = Icons.Default.Refresh,
                    text =
                        androidx.compose.ui.res.stringResource(
                            com.singam.lionlibrary.R.string.ui_start_from_beginning
                        ),
                    onClick = {
                        onAction(HomeAction.SelectOptions(null))
                        onAction(HomeAction.OnStartFromBeginning(item))
                    },
                    iconTint = MaterialTheme.colorScheme.primary,
                )
                JumpBackInOptionItem(
                    icon = Icons.Default.Delete,
                    text =
                        androidx.compose.ui.res.stringResource(
                            com.singam.lionlibrary.R.string.ui_remove
                        ),
                    onClick = {
                        onAction(HomeAction.SelectOptions(null))
                        onAction(HomeAction.OnRemoveWatchProgress(item))
                    },
                    iconTint = MaterialTheme.colorScheme.primary,
                )
                JumpBackInOptionItem(
                    icon = Icons.Default.Delete,
                    text =
                        androidx.compose.ui.res.stringResource(
                            com.singam.lionlibrary.R.string.delete_from_device
                        ),
                    onClick = { onAction(HomeAction.RequestDelete(item.mediaId)) },
                    iconTint = MaterialTheme.colorScheme.error,
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
    iconTint: Color = Color.White,
) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = iconTint,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(24.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
        )
    }
}

private const val HERO_AUTO_SCROLL_MS = 8_000L

@androidx.compose.foundation.ExperimentalFoundationApi
@Composable
internal fun HeroBannerCarousel(
    mediaItems: List<MediaItem>,
    onInfoClick: (Long, MediaType) -> Unit,
    modifier: Modifier = Modifier,
    verticalScrollOffset: () -> Float = { 0f },
    onItemLongClick: (MediaItem) -> Unit = {},
) {
    if (mediaItems.isEmpty()) return
    val pagerState =
        androidx.compose.foundation.pager.rememberPagerState(pageCount = { mediaItems.size })
    // Rotation is decorative. Stop after interaction and for accessibility/reduced motion.
    val context = LocalContext.current
    val accessibility =
        remember(context) {
            context.getSystemService(android.content.Context.ACCESSIBILITY_SERVICE)
                as android.view.accessibility.AccessibilityManager
        }
    var exploring by remember {
        androidx.compose.runtime.mutableStateOf(accessibility.isTouchExplorationEnabled)
    }
    androidx.compose.runtime.DisposableEffect(accessibility) {
        val listener =
            android.view.accessibility.AccessibilityManager.TouchExplorationStateChangeListener {
                exploring = it
            }
        accessibility.addTouchExplorationStateChangeListener(listener)
        onDispose { accessibility.removeTouchExplorationStateChangeListener(listener) }
    }
    var interacted by
        androidx.compose.runtime.saveable.rememberSaveable {
            androidx.compose.runtime.mutableStateOf(false)
        }
    val dragging by pagerState.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(dragging) { if (dragging) interacted = true }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val lifecycleState by lifecycle.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    val motionEnabled =
        android.provider.Settings.Global.getFloat(
            context.contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) > 0f
    LaunchedEffect(
        pagerState.settledPage,
        mediaItems.size,
        exploring,
        interacted,
        lifecycleState,
        motionEnabled,
    ) {
        if (
            mediaItems.size <= 1 ||
                exploring ||
                interacted ||
                !motionEnabled ||
                !lifecycleState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
        )
            return@LaunchedEffect
        delay(HERO_AUTO_SCROLL_MS)
        if (!pagerState.isScrollInProgress)
            pagerState.animateScrollToPage(
                (pagerState.settledPage + 1) % mediaItems.size,
                animationSpec = tween(LionMotion.Standard, easing = LionMotion.Easing),
            )
    }

    val configuration = LocalConfiguration.current
    val fontScale = LocalDensity.current.fontScale
    val heroHeight =
        (configuration.screenHeightDp.dp * .58f).coerceIn(360.dp, 600.dp) +
            (80.dp * (fontScale - 1f).coerceIn(0f, 1f))
    val logoHeight = 80.dp * fontScale.coerceIn(1f, 1.6f)
    val background = MaterialTheme.colorScheme.background
    val optionsLabel =
        androidx.compose.ui.res.stringResource(com.singam.lionlibrary.R.string.media_options)
    val scrim = MaterialTheme.colorScheme.scrim
    val gradient =
        remember(background, scrim) {
            Brush.verticalGradient(
                colorStops =
                    arrayOf(
                        0f to scrim.copy(alpha = .1f),
                        .45f to Color.Transparent,
                        .72f to scrim.copy(alpha = .55f),
                        1f to background,
                    )
            )
        }
    Column(modifier.fillMaxWidth().height(heroHeight)) {
        Box(Modifier.fillMaxWidth().weight(1f).clip(androidx.compose.ui.graphics.RectangleShape)) {
            // Pager supplies native swipe/fling semantics. Visual layers move less than a full
            // page and crossfade; read offsets in draw to keep frames out of composition.
            androidx.compose.foundation.pager.HorizontalPager(
                state = pagerState,
                modifier =
                    Modifier.fillMaxSize()
                        .testTag("hero-interaction")
                        .semantics {
                            onLongClick(optionsLabel) {
                                interacted = true
                                onItemLongClick(mediaItems[pagerState.currentPage])
                                true
                            }
                        }
                        .pointerInput(mediaItems) {
                            detectTapGestures(
                                onLongPress = {
                                    interacted = true
                                    onItemLongClick(mediaItems[pagerState.currentPage])
                                }
                            )
                        },
                key = { mediaItems[it].id },
            ) {
                Box(Modifier.fillMaxSize())
            }
            val visiblePages =
                ((pagerState.currentPage - 1).coerceAtLeast(0)..(pagerState.currentPage + 1)
                        .coerceAtMost(mediaItems.lastIndex))
            visiblePages.forEach { page ->
                key(mediaItems[page].id) {
                    val media = mediaItems[page]
                    LocalArtwork(
                        listOf(media.backdropPath, media.posterPath),
                        null,
                        Modifier.fillMaxSize().graphicsLayer {
                            val offset =
                                (pagerState.currentPage - page) +
                                    pagerState.currentPageOffsetFraction
                            alpha = (1f - abs(offset)).coerceIn(0f, 1f)
                            translationX = if (motionEnabled) -offset * size.width * .055f else 0f
                            translationY = if (motionEnabled) verticalScrollOffset() * .22f else 0f
                            scaleX = if (motionEnabled) 1.14f else 1f
                            scaleY = if (motionEnabled) 1.14f else 1f
                        },
                    )
                }
            }
            Box(Modifier.fillMaxSize().background(gradient))
            visiblePages.forEach { page ->
                key(mediaItems[page].id) {
                    val media = mediaItems[page]
                    Column(
                        Modifier.align(Alignment.BottomCenter)
                            .graphicsLayer {
                                val offset =
                                    (pagerState.currentPage - page) +
                                        pagerState.currentPageOffsetFraction
                                alpha = (1f - abs(offset)).coerceIn(0f, 1f)
                                translationX =
                                    if (motionEnabled) -offset * size.width * .18f else 0f
                            }
                            .then(
                                if (page != pagerState.currentPage) Modifier.clearAndSetSemantics {}
                                else Modifier
                            )
                            .padding(horizontal = LionLayout.Gutter, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        ArtworkTitle(
                            media.title,
                            media.logoPath,
                            Modifier.fillMaxWidth(.72f).height(logoHeight),
                        )
                        HeroMetadataLine(
                            media.year?.toString(),
                            media.genres,
                            media.rating
                                ?.takeIf { it > 0f }
                                ?.let {
                                    androidx.compose.ui.res.stringResource(
                                        com.singam.lionlibrary.R.string.star_rating_value,
                                        it,
                                    )
                                },
                        )
                    }
                }
            }
        }
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Wait until a swipe settles so the action always opens the title currently on screen.
            val current = mediaItems[pagerState.settledPage.coerceIn(mediaItems.indices)]
            Button(
                onClick = {
                    interacted = true
                    onInfoClick(current.id, current.mediaType)
                },
                enabled = !pagerState.isScrollInProgress,
                shape = RoundedCornerShape(50),
                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp),
                modifier = Modifier.heightIn(min = 48.dp),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onBackground,
                        contentColor = MaterialTheme.colorScheme.background,
                        disabledContainerColor = MaterialTheme.colorScheme.onBackground,
                        disabledContentColor = MaterialTheme.colorScheme.background,
                    ),
            ) {
                Text(
                    androidx.compose.ui.res.stringResource(
                        com.singam.lionlibrary.R.string.ui_view_details
                    ),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            if (mediaItems.size > 1) {
                val neutral = MaterialTheme.colorScheme.onBackground
                val accent = MaterialTheme.colorScheme.primary
                Canvas(
                    Modifier.padding(top = 16.dp)
                        .width((mediaItems.size * 16 + 16).dp)
                        .height(8.dp)
                        .testTag("hero-indicators")
                ) {
                    var x = 0f
                    repeat(mediaItems.size) { index ->
                        val offset =
                            (pagerState.currentPage - index) + pagerState.currentPageOffsetFraction
                        val active = (1f - abs(offset)).coerceIn(0f, 1f)
                        val width = (8 + 24 * active).dp.toPx()
                        drawRoundRect(
                            lerp(neutral.copy(alpha = .35f), accent, active),
                            topLeft = Offset(x, 0f),
                            size = Size(width, size.height),
                            cornerRadius = CornerRadius(size.height / 2f),
                        )
                        x += width + 8.dp.toPx()
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
    onHeaderClick: (() -> Unit)? = null,
    onItemLongClick: (MediaItem) -> Unit = {},
) {
    Column(modifier = Modifier.padding(bottom = LionLayout.SectionGap)) {
        ShelfHeading(
            title,
            Modifier.padding(horizontal = LionLayout.Gutter).padding(bottom = 10.dp),
            onClick = onHeaderClick,
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items, key = { it.id }) { item ->
                MediaCard(
                    mediaItem = item,
                    onClick = { onItemClick(item) },
                    onLongClick = { onItemLongClick(item) },
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
    onItemLongClick: (JumpBackInItem) -> Unit = {},
) {
    Column(modifier = Modifier.padding(bottom = LionLayout.SectionGap)) {
        ShelfHeading(
            title,
            Modifier.padding(horizontal = LionLayout.Gutter).padding(bottom = 10.dp),
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items, key = { "${it.mediaId}-${it.episodeId}" }) { item ->
                JumpBackInCard(
                    item = item,
                    onClick = { onItemClick(item) },
                    onLongClick = { onItemLongClick(item) },
                )
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Hero with one local title")
@androidx.compose.ui.tooling.preview.Preview(
    name = "Hero enlarged text",
    widthDp = 320,
    fontScale = 2f,
)
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun SingleHeroPreview() {

    com.singam.lionlibrary.ui.theme.LionLibraryTheme {
        HeroBannerCarousel(
            listOf(com.singam.lionlibrary.presentation.components.PreviewFilm),
            { _, _ -> },
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Hero carousel", widthDp = 840)
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun CarouselPreview() {

    com.singam.lionlibrary.ui.theme.LionLibraryTheme {
        val film = com.singam.lionlibrary.presentation.components.PreviewFilm

        HeroBannerCarousel(listOf(film, film.copy(id = 2, title = "Michael")), { _, _ -> })
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Media shelf")
@Composable
private fun ShelfPreview() {

    com.singam.lionlibrary.ui.theme.LionLibraryTheme {
        MediaRow(
            "Movies",
            listOf(com.singam.lionlibrary.presentation.components.PreviewFilm),
            {},
            {},
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Continue watching shelf")
@Composable
private fun ResumeShelfPreview() {

    com.singam.lionlibrary.ui.theme.LionLibraryTheme {
        JumpBackInRow(
            "Jump Back In",
            listOf(
                JumpBackInItem(
                    1,
                    MediaType.MOVIE,
                    "Michael",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    .5f,
                    filePath = "content://fixture/movie",
                    lastWatched = 1,
                )
            ),
            {},
            {},
        )
    }
}
