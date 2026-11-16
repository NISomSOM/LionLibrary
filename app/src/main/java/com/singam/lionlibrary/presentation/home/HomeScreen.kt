package com.singam.lionlibrary.presentation.home

import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.singam.lionlibrary.R
import com.singam.lionlibrary.domain.model.JumpBackInItem
import com.singam.lionlibrary.domain.model.MediaFilter
import com.singam.lionlibrary.domain.model.MediaItem
import com.singam.lionlibrary.domain.model.MediaType
import com.singam.lionlibrary.presentation.components.ArtworkTitle
import com.singam.lionlibrary.presentation.components.DeletionDialog
import com.singam.lionlibrary.presentation.components.HomeSkeleton
import com.singam.lionlibrary.presentation.components.JumpBackInCard
import com.singam.lionlibrary.presentation.components.LocalArtwork
import com.singam.lionlibrary.presentation.components.MediaCard
import com.singam.lionlibrary.presentation.components.MediaOptionItem
import com.singam.lionlibrary.presentation.components.PosterPreview
import com.singam.lionlibrary.presentation.components.PreviewFilm
import com.singam.lionlibrary.presentation.components.ShelfHeading
import com.singam.lionlibrary.presentation.components.rememberBackdropAlignment
import com.singam.lionlibrary.presentation.navigation.LocalFloatingNavigationInset
import com.singam.lionlibrary.ui.theme.LionLayout
import com.singam.lionlibrary.ui.theme.LionLibraryTheme
import com.singam.lionlibrary.ui.theme.LionMotion
import com.singam.lionlibrary.ui.theme.LionTheme
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@ExperimentalFoundationApi
@Composable
fun HomeRoot(
    viewModel: HomeViewModel = koinViewModel(),
    snackbarHostState: SnackbarHostState,
    windowSizeClass: WindowSizeClass,
    onNavigateToMovieDetails: (Long) -> Unit,
    onNavigateToShowDetails: (Long) -> Unit,
    onNavigateToPlayer: (MediaType, Long) -> Unit,
    onNavigateToLibrary: (MediaFilter) -> Unit,
    onSettingsClick: () -> Unit = {},
    onMediaClick: (Long, MediaType) -> Unit = { _, _ -> },
    onLibraryClick: (MediaFilter) -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
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
                        showSnackbar(resources.getString(R.string.external_launch_error))
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
@ExperimentalFoundationApi
@Composable
fun HomeScreen(
    state: HomeState,
    windowSizeClass: WindowSizeClass,
    onAction: (HomeAction) -> Unit,
    onSettingsClick: () -> Unit = {},
    onMediaClick: (Long, MediaType) -> Unit = { _, _ -> },
    onLibraryClick: (MediaFilter) -> Unit = {},
) {
    val selectedJumpBackInItem = state.selectedJumpBackInItem
    val sheetState = rememberModalBottomSheetState()
    DeletionDialog(
        state.deletion,
        { onAction(HomeAction.ConfirmDelete) },
        { onAction(HomeAction.DismissDelete) },
    )
    // Poster long press: lifted preview over a blurred/dimmed page, same actions as before.
    state.selectedMediaForOptions?.let { media ->
        PosterPreview(
            media = media,
            onOpen = {
                onAction(HomeAction.MediaOptions(null))
                onMediaClick(media.id, media.mediaType)
            },
            onDismiss = { onAction(HomeAction.MediaOptions(null)) },
        ) {
            MediaOptionItem(
                Icons.Default.Delete,
                stringResource(R.string.delete_from_device),
                { onAction(HomeAction.RequestDelete(media.id)) },
                iconTint = MaterialTheme.colorScheme.error,
            )
        }
    }

    if (state.isLoading) {
        HomeSkeleton()
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
                    text = stringResource(R.string.library_empty_help),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                )
                Button(onClick = onSettingsClick) {
                    Text(stringResource(R.string.open_settings))
                }
            }
        }
        return
    }

    val listState = rememberLazyListState()
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
                    title = stringResource(R.string.ui_jump_back_in),
                    items = state.jumpBackInItems,
                    onItemClick = { onAction(HomeAction.OnJumpBackInClick(it)) },
                    onItemLongClick = { onAction(HomeAction.SelectOptions(it)) },
                )
            }
        }

        if (state.movies.isNotEmpty()) {
            item(key = "movies", contentType = "shelf") {
                MediaRow(
                    title = stringResource(R.string.movies),
                    items = state.movies,
                    onItemClick = { onMediaClick(it.id, it.mediaType) },
                    onItemLongClick = { onAction(HomeAction.MediaOptions(it)) },
                    shelfKey = "movies",
                    onHeaderClick = {
                        onLibraryClick(MediaFilter.MOVIES)
                    },
                )
            }
        }

        if (state.tvShows.isNotEmpty()) {
            item(key = "tv-shows", contentType = "shelf") {
                MediaRow(
                    title = stringResource(R.string.tv_shows),
                    items = state.tvShows,
                    onItemClick = { onMediaClick(it.id, it.mediaType) },
                    onItemLongClick = { onAction(HomeAction.MediaOptions(it)) },
                    shelfKey = "tv-shows",
                    onHeaderClick = {
                        onLibraryClick(MediaFilter.TV_SHOWS)
                    },
                )
            }
        }

        if (state.anime.isNotEmpty()) {
            item(key = "anime", contentType = "shelf") {
                MediaRow(
                    title = stringResource(R.string.anime),
                    items = state.anime,
                    onItemClick = { onMediaClick(it.id, it.mediaType) },
                    onItemLongClick = { onAction(HomeAction.MediaOptions(it)) },
                    shelfKey = "anime",
                    onHeaderClick = {
                        onLibraryClick(MediaFilter.ANIME)
                    },
                )
            }
        }

        if (state.recentlyAdded.isNotEmpty()) {
            item(key = "recently-added", contentType = "shelf") {
                MediaRow(
                    title = stringResource(R.string.ui_recently_added),
                    items = state.recentlyAdded,
                    onItemClick = { onMediaClick(it.id, it.mediaType) },
                    onItemLongClick = { onAction(HomeAction.MediaOptions(it)) },
                    shelfKey = "recently-added",
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
                        shelfKey = "genre:$genre",
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
                            color = LionTheme.colors.onMedia,
                        )
                        if (item.mediaType != MediaType.MOVIE) {
                            Text(
                                text =
                                    stringResource(
                                        R.string.jump_back_in_episode,
                                        item.seasonNumber ?: 0,
                                        item.episodeNumber ?: 0,
                                        item.episodeTitle.orEmpty(),
                                    ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = LionTheme.colors.textSecondary,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                MediaOptionItem(
                    icon = Icons.Default.Info,
                    text = stringResource(R.string.ui_go_to_details),
                    onClick = {
                        onAction(HomeAction.SelectOptions(null))
                        onMediaClick(item.mediaId, item.mediaType)
                    },
                )
                MediaOptionItem(
                    icon = Icons.Default.PlayArrow,
                    text = stringResource(R.string.ui_play_in_external_player),
                    onClick = {
                        onAction(HomeAction.SelectOptions(null))
                        onAction(HomeAction.OnPlayExternal(item))
                    },
                    iconTint = MaterialTheme.colorScheme.primary,
                )
                MediaOptionItem(
                    icon = Icons.Default.Refresh,
                    text = stringResource(R.string.ui_start_from_beginning),
                    onClick = {
                        onAction(HomeAction.SelectOptions(null))
                        onAction(HomeAction.OnStartFromBeginning(item))
                    },
                    iconTint = MaterialTheme.colorScheme.primary,
                )
                MediaOptionItem(
                    icon = Icons.Default.Delete,
                    text = stringResource(R.string.ui_remove),
                    onClick = {
                        onAction(HomeAction.SelectOptions(null))
                        onAction(HomeAction.OnRemoveWatchProgress(item))
                    },
                    iconTint = MaterialTheme.colorScheme.primary,
                )
                MediaOptionItem(
                    icon = Icons.Default.Delete,
                    text = stringResource(R.string.delete_from_device),
                    onClick = { onAction(HomeAction.RequestDelete(item.mediaId)) },
                    iconTint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

private const val HERO_AUTO_SCROLL_MS = 8_000L

@ExperimentalFoundationApi
@Composable
internal fun HeroBannerCarousel(
    mediaItems: List<MediaItem>,
    onInfoClick: (Long, MediaType) -> Unit,
    modifier: Modifier = Modifier,
    verticalScrollOffset: () -> Float = { 0f },
    onItemLongClick: (MediaItem) -> Unit = {},
) {
    if (mediaItems.isEmpty()) return
    val pagerState = rememberPagerState(pageCount = { mediaItems.size })
    // Rotation is decorative. Stop after interaction and for accessibility/reduced motion.
    val context = LocalContext.current
    val accessibility =
        remember(context) {
            context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        }
    var exploring by remember {
        mutableStateOf(accessibility.isTouchExplorationEnabled)
    }
    DisposableEffect(accessibility) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener {
            exploring = it
        }
        accessibility.addTouchExplorationStateChangeListener(listener)
        onDispose { accessibility.removeTouchExplorationStateChangeListener(listener) }
    }
    var interacted by
        androidx.compose.runtime.saveable.rememberSaveable {
            mutableStateOf(false)
        }
    val dragging by pagerState.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(dragging) { if (dragging) interacted = true }
    val lifecycle = LocalLifecycleOwner.current
    val lifecycleState by lifecycle.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    // Read once per lifecycle change rather than every recomposition; returning from system
    // settings resumes the screen, so a changed animation scale is still picked up.
    val motionEnabled =
        remember(context, lifecycleState) {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) > 0f
        }
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
                !lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
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
    val optionsLabel = stringResource(R.string.media_options)
    val haptics = LocalHapticFeedback.current
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
        Box(Modifier.fillMaxWidth().weight(1f).clip(RectangleShape)) {
            // Pager supplies native swipe/fling semantics. Visual layers move less than a full
            // page and crossfade; read offsets in draw to keep frames out of composition.
            HorizontalPager(
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
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
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
                    // Tall hero over a wide backdrop: bias the crop toward the subject.
                    val focus = rememberBackdropAlignment(media.backdropPath ?: media.posterPath)
                    LocalArtwork(
                        listOf(media.backdropPath, media.posterPath),
                        null,
                        alignment = focus,
                        modifier =
                            Modifier.fillMaxSize().graphicsLayer {
                                val offset =
                                    (pagerState.currentPage - page) +
                                        pagerState.currentPageOffsetFraction
                                alpha = (1f - abs(offset)).coerceIn(0f, 1f)
                                translationX =
                                    if (motionEnabled) -offset * size.width * .055f else 0f
                                translationY =
                                    if (motionEnabled) verticalScrollOffset() * .22f else 0f
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
                        HeroMetadata(
                            mediaType = media.mediaType,
                            genres = media.genres,
                            durationMinutes = media.duration,
                            year = media.year,
                            tmdbRating = media.rating,
                            letterboxdRating = media.letterboxdRating,
                            language = media.originalLanguage,
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
                    stringResource(R.string.ui_view_details),
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
    // Identifies this shelf for the poster-to-Details transition; null keeps the plain fade.
    shelfKey: String? = null,
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
                    modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null),
                    sharedShelf = shelfKey,
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
            // One card per title, keyed by title: a title moving to the front (just watched) or its
            // next-up episode changing animates as a move/update instead of remove + add.
            items(items, key = { it.mediaId }) { item ->
                JumpBackInCard(
                    item = item,
                    onClick = { onItemClick(item) },
                    onLongClick = { onItemLongClick(item) },
                    modifier =
                        Modifier.animateItem(
                            fadeInSpec =
                                tween(LionMotion.Standard, easing = LionMotion.EnterEasing),
                            placementSpec =
                                spring(
                                    stiffness = Spring.StiffnessMediumLow,
                                    visibilityThreshold = IntOffset.VisibilityThreshold,
                                ),
                            fadeOutSpec = tween(LionMotion.Fast, easing = LionMotion.ExitEasing),
                        ),
                )
            }
        }
    }
}

@Preview(name = "Hero with one local title")
@Preview(
    name = "Hero enlarged text",
    widthDp = 320,
    fontScale = 2f,
)
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SingleHeroPreview() {

    LionLibraryTheme {
        HeroBannerCarousel(
            listOf(PreviewFilm),
            { _, _ -> },
        )
    }
}

@Preview(name = "Hero carousel", widthDp = 840)
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CarouselPreview() {

    LionLibraryTheme {
        val film = PreviewFilm

        HeroBannerCarousel(listOf(film, film.copy(id = 2, title = "Michael")), { _, _ -> })
    }
}

@Preview(name = "Media shelf")
@Composable
private fun ShelfPreview() {

    LionLibraryTheme {
        MediaRow(
            "Movies",
            listOf(PreviewFilm),
            {},
            {},
        )
    }
}

@Preview(name = "Continue watching shelf")
@Composable
private fun ResumeShelfPreview() {

    LionLibraryTheme {
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
