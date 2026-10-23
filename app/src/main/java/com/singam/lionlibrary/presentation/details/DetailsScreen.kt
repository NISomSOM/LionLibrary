package com.singam.lionlibrary.presentation.details

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.singam.lionlibrary.domain.model.Episode
import com.singam.lionlibrary.domain.model.MediaItem
import com.singam.lionlibrary.domain.model.MediaType
import com.singam.lionlibrary.domain.model.isSingleFile
import com.singam.lionlibrary.presentation.components.ArtworkTitle
import com.singam.lionlibrary.presentation.components.EpisodeCard
import com.singam.lionlibrary.presentation.components.LocalArtwork
import com.singam.lionlibrary.ui.theme.LionLayout
import com.singam.lionlibrary.ui.theme.LionMotion
import org.koin.androidx.compose.koinViewModel

@Composable
fun DetailsRoot(
    snackbarHostState: SnackbarHostState,
    windowSizeClass: WindowSizeClass,
    onNavigateToPlayer: (String, Long) -> Unit,
    onBack: () -> Unit = {},
    viewModel: DetailsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = androidx.compose.ui.platform.LocalResources.current

    LaunchedEffect(viewModel.events) {
        viewModel.events.collect { event ->
            when (event) {
                DetailsEvent.NavigateBack -> onBack()
                is DetailsEvent.ShowMessage ->
                    snackbarHostState.showSnackbar(event.message.resolve(context))
                is DetailsEvent.NavigateToPlayer -> {
                    onNavigateToPlayer(event.mediaType, event.mediaId)
                }
                is DetailsEvent.ShowError -> {
                    snackbarHostState.showSnackbar(event.message.resolve(context))
                }
                is DetailsEvent.LaunchPlayer -> {
                    try {
                        context.startActivity(event.intent)
                    } catch (e: Exception) {
                        snackbarHostState.showSnackbar(
                            resources.getString(
                                com.singam.lionlibrary.R.string.external_launch_error
                            )
                        )
                    }
                }
                is DetailsEvent.OpenUrl -> {
                    try {
                        context.startActivity(
                            android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(event.url),
                            )
                        )
                    } catch (e: android.content.ActivityNotFoundException) {
                        snackbarHostState.showSnackbar(
                            resources.getString(com.singam.lionlibrary.R.string.error_not_found)
                        )
                    }
                }
            }
        }
    }

    DetailsScreen(
        state = state,
        windowSizeClass = windowSizeClass,
        onAction = viewModel::onAction,
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsScreen(
    state: DetailsState,
    windowSizeClass: WindowSizeClass,
    onAction: (DetailsAction) -> Unit,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
) {
    val selectedEpisodeForOptions = state.selectedEpisodeForOptions
    val sheetState = rememberModalBottomSheetState()
    com.singam.lionlibrary.presentation.components.DeletionDialog(
        state.deletion,
        { onAction(DetailsAction.ConfirmDelete) },
        { onAction(DetailsAction.DismissDelete) },
    )

    if (state.isLoading) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val media =
        state.media
            ?: run {
                Box(
                    Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        state.error?.resolve(LocalContext.current)
                            ?: androidx.compose.ui.res.stringResource(
                                com.singam.lionlibrary.R.string.file_access_error
                            ),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                return
            }
    val listState = rememberLazyListState()
    // Start when real content arrives, even if the navigation fade ended during loading.
    // The remembered value stays opaque through scrolling, history updates and player return.
    val contentAlpha = remember(media.id) { Animatable(0f) }
    LaunchedEffect(media.id) { contentAlpha.animateTo(1f, tween(240, easing = LionMotion.Easing)) }

    var toolbarHeightPx by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var primaryActionAboveToolbar by remember(media.id) { mutableStateOf(false) }
    val showTopBarBackground by
        remember(media.id, listState) {
            derivedStateOf { listState.firstVisibleItemIndex > 0 || primaryActionAboveToolbar }
        }
    val onPrimaryActionBottomChanged: (Float) -> Unit = { bottom ->
        primaryActionAboveToolbar = toolbarHeightPx > 0 && bottom <= toolbarHeightPx
    }
    val topBarColor =
        if (showTopBarBackground) MaterialTheme.colorScheme.surface else Color.Transparent
    val logoAlpha by
        animateFloatAsState(
            targetValue = if (showTopBarBackground) 1f else 0f,
            animationSpec = tween(LionMotion.Fast, easing = LionMotion.Easing),
            label = "toolbarLogo",
        )

    selectedEpisodeForOptions?.let { episode ->
        ModalBottomSheet(
            onDismissRequest = { onAction(DetailsAction.SelectOptions(null)) },
            sheetState = sheetState,
            containerColor = Color(0xFF1E1E1E),
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val imagePath = episode.thumbnailPath
                    if (imagePath != null) {
                        LocalArtwork(
                            paths = listOf(imagePath),
                            contentDescription = episode.title,
                            contentScale = ContentScale.Crop,
                            modifier =
                                Modifier.width(125.dp).height(70.dp).clip(RoundedCornerShape(8.dp)),
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = episode.title ?: "Episode ${episode.episodeNumber}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                        Text(
                            text = "Season ${episode.seasonNumber}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                EpisodeOptionItem(
                    icon = Icons.Default.CheckCircle,
                    text =
                        androidx.compose.ui.res.stringResource(
                            com.singam.lionlibrary.R.string.ui_mark_as_watched_upto_here
                        ),
                    onClick = {
                        onAction(DetailsAction.SelectOptions(null))
                        onAction(DetailsAction.OnMarkWatchedUpTo(episode))
                    },
                )
                EpisodeOptionItem(
                    icon = Icons.Default.PlayArrow,
                    text =
                        androidx.compose.ui.res.stringResource(
                            com.singam.lionlibrary.R.string.ui_play_in_external_player
                        ),
                    onClick = {
                        onAction(DetailsAction.SelectOptions(null))
                        onAction(DetailsAction.OnPlayEpisodeExternal(episode))
                    },
                    iconTint = Color(0xFFE5B13A),
                )
                EpisodeOptionItem(
                    icon = androidx.compose.material.icons.Icons.Default.Delete,
                    text =
                        androidx.compose.ui.res.stringResource(
                            com.singam.lionlibrary.R.string.delete_from_device
                        ),
                    onClick = { onAction(DetailsAction.RequestDeleteEpisode(episode.id)) },
                    iconTint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isTablet = configuration.smallestScreenWidthDp >= 600
    val isPortrait =
        configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT
    val isLandscapeMode = !isPortrait

    val isTwoPane = isTablet && isLandscapeMode && !media.isSingleFile
    val isTabletPortrait = isTablet && isPortrait
    val isTabletPortraitMovie = isTabletPortrait && media.isSingleFile

    Box(modifier = modifier.fillMaxSize().graphicsLayer { alpha = contentAlpha.value }) {
        if (isTwoPane) {
            Row(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    HeroHeaderSection(media, state, isLandscapeMode, onAction)
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1.5f),
                    contentPadding =
                        PaddingValues(bottom = 32.dp, top = if (isLandscapeMode) 16.dp else 64.dp),
                ) {
                    seasonSelectorAndEpisodes(
                        media,
                        state,
                        onAction,
                        { onAction(DetailsAction.SelectOptions(it)) },
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().testTag("details-list"),
                contentPadding = PaddingValues(bottom = 32.dp),
            ) {
                item {
                    val heroModifier =
                        if (isTabletPortraitMovie) Modifier.fillParentMaxHeight() else Modifier
                    HeroHeaderSection(
                        media,
                        state,
                        isLandscapeMode,
                        onAction,
                        heroModifier,
                        isTabletPortraitMovie,
                        onPrimaryActionBottomChanged,
                    )
                }
                seasonSelectorAndEpisodes(
                    media,
                    state,
                    onAction,
                    { onAction(DetailsAction.SelectOptions(it)) },
                )
            }
        }

        if (!isLandscapeMode) {
            TopAppBar(
                modifier =
                    Modifier.onSizeChanged { toolbarHeightPx = it.height }
                        .testTag(
                            if (showTopBarBackground) "details-toolbar-visible"
                            else "details-toolbar-hidden"
                        ),
                title = {
                    Box(
                        modifier =
                            Modifier.fillMaxWidth()
                                .padding(end = 48.dp)
                                .then(
                                    if (!showTopBarBackground) Modifier.clearAndSetSemantics {}
                                    else Modifier
                                )
                                .graphicsLayer { alpha = if (isTwoPane) 1f else logoAlpha },
                        contentAlignment = Alignment.Center,
                    ) {
                        ArtworkTitle(
                            media.title,
                            media.logoPath,
                            Modifier.fillMaxWidth(0.8f).height(40.dp),
                            compact = true,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription =
                                androidx.compose.ui.res.stringResource(
                                    com.singam.lionlibrary.R.string.back
                                ),
                            tint = Color.White,
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = if (isTwoPane) Color(0xFF141414) else topBarColor,
                        titleContentColor = Color.White,
                    ),
            )
        } else {
            IconButton(onClick = onBack, modifier = Modifier.padding(16.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription =
                        androidx.compose.ui.res.stringResource(
                            com.singam.lionlibrary.R.string.back
                        ),
                    tint = Color.White,
                    modifier =
                        Modifier.size(36.dp)
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
                            .padding(6.dp),
                )
            }
        }
    }
}

@Composable
private fun HeroHeaderSection(
    media: MediaItem,
    state: DetailsState,
    isLandscapeMode: Boolean,
    onAction: (DetailsAction) -> Unit,
    modifier: Modifier = Modifier,
    isTabletPortraitMovie: Boolean = false,
    onPrimaryActionBottomChanged: (Float) -> Unit = {},
) {
    Column(modifier = modifier.fillMaxWidth()) {
        val imageModifier =
            if (isTabletPortraitMovie) {
                Modifier.fillMaxWidth().weight(1f)
            } else {
                if (isLandscapeMode) Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                else
                    Modifier.fillMaxWidth()
                        .height(
                            (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
                                    .dp * 1.33f)
                                .coerceIn(420.dp, 760.dp)
                        )
            }
        Box(modifier = imageModifier) {
            LocalArtwork(
                paths = listOf(media.backdropPath, media.posterPath),
                contentDescription = media.title,
                modifier =
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
            )

            Box(
                modifier =
                    Modifier.fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors =
                                    listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.4f),
                                        MaterialTheme.colorScheme.background,
                                    ),
                                startY = 0f,
                                endY = Float.POSITIVE_INFINITY,
                            )
                        )
            )

            Box(
                modifier =
                    Modifier.fillMaxSize()
                        .padding(horizontal = LionLayout.DetailGutter)
                        .padding(bottom = 8.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ArtworkTitle(
                        media.title,
                        media.logoPath,
                        Modifier.fillMaxWidth(.72f)
                            .height(
                                80.dp *
                                    androidx.compose.ui.platform.LocalDensity.current.fontScale
                                        .coerceIn(1f, 1.6f)
                            ),
                    )

                    if (!media.genres.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        val genresText =
                            media.genres.split(",").take(3).joinToString(" • ") { it.trim() }
                        Text(
                            text = genresText,
                            style =
                                MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Medium
                                ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(horizontal = LionLayout.DetailGutter)
                    .padding(top = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Button(
                    onClick = {
                        if (media.isSingleFile) onAction(DetailsAction.OnPlayMovie)
                        else onAction(DetailsAction.OnResumeTvShow)
                    },
                    modifier =
                        Modifier.weight(1f)
                            .heightIn(min = 52.dp)
                            .testTag("details-primary-action")
                            .onGloballyPositioned {
                                onPrimaryActionBottomChanged(it.boundsInRoot().bottom)
                            },
                    shape = RoundedCornerShape(100),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.onBackground,
                            contentColor = MaterialTheme.colorScheme.background,
                        ),
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription =
                            androidx.compose.ui.res.stringResource(
                                com.singam.lionlibrary.R.string.play
                            ),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    val buttonText = androidx.compose.ui.res.stringResource(state.playbackLabel())
                    Text(
                        buttonText,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                OutlinedIconButton(
                    onClick = { onAction(DetailsAction.ToggleWatchlist) },
                    modifier = Modifier.size(48.dp),
                    border =
                        androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outline,
                        ),
                ) {
                    Icon(
                        if (media.isWatchlisted) Icons.Filled.Bookmark
                        else Icons.Outlined.BookmarkBorder,
                        modifier = Modifier.size(LionLayout.BookmarkGlyph),
                        contentDescription =
                            androidx.compose.ui.res.stringResource(
                                if (media.isWatchlisted)
                                    com.singam.lionlibrary.R.string.remove_watchlist
                                else com.singam.lionlibrary.R.string.add_watchlist
                            ),
                        tint =
                            if (media.isWatchlisted) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onBackground,
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val yearText = media.year?.toString() ?: ""
                val durationText =
                    media.duration?.let { dur ->
                        val hours = dur / 60
                        val mins = dur % 60
                        if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
                    } ?: ""

                val combinedText =
                    if (yearText.isNotEmpty() && durationText.isNotEmpty())
                        "$yearText - $durationText"
                    else if (yearText.isNotEmpty()) yearText else durationText

                if (combinedText.isNotEmpty()) {
                    Text(
                        text = combinedText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }

                if (!media.certification.isNullOrBlank()) {
                    Box(
                        modifier =
                            Modifier.border(1.dp, Color.Gray, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = media.certification,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (media.rating != null && media.rating > 0.0) {
                    Row(
                        modifier = Modifier.width(IntrinsicSize.Max),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text =
                                androidx.compose.ui.res.stringResource(
                                    com.singam.lionlibrary.R.string.tmdb_rating
                                ),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text =
                                androidx.compose.ui.res.stringResource(
                                    com.singam.lionlibrary.R.string.rating_value,
                                    media.rating,
                                ),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.Yellow,
                        )
                    }
                }

                if (media.isSingleFile && media.tmdbId != null) {
                    androidx.compose.material3.TextButton(
                        onClick = { onAction(DetailsAction.OnOpenLetterboxd) },
                        modifier = Modifier.width(IntrinsicSize.Max),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                    ) {
                        Text(
                            text =
                                androidx.compose.ui.res.stringResource(
                                    com.singam.lionlibrary.R.string.ui_letterboxd
                                ),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                        media.letterboxdRating?.let { lb ->
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text =
                                    androidx.compose.ui.res.stringResource(
                                        com.singam.lionlibrary.R.string.star_rating_value,
                                        lb,
                                    ),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF00E054),
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (!media.overview.isNullOrBlank()) {
                var isExpanded by
                    androidx.compose.runtime.saveable.rememberSaveable(media.id) {
                        mutableStateOf(false)
                    }
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
                    Text(
                        text = media.overview,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = if (isExpanded) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis,
                    )

                    androidx.compose.material3.TextButton(
                        onClick = { isExpanded = !isExpanded },
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Text(
                            androidx.compose.ui.res.stringResource(
                                if (isExpanded) com.singam.lionlibrary.R.string.show_less
                                else com.singam.lionlibrary.R.string.show_more
                            ),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

private fun LazyListScope.seasonSelectorAndEpisodes(
    media: MediaItem,
    state: DetailsState,
    onAction: (DetailsAction) -> Unit,
    onEpisodeLongClick: (Episode) -> Unit,
) {
    if (
        (media.mediaType == MediaType.TV_SHOW || media.mediaType == MediaType.ANIME) &&
            state.seasons.isNotEmpty()
    ) {
        val selectedSeasonIndex =
            state.seasons
                .indexOfFirst { it.seasonNumber == state.selectedSeasonNumber }
                .coerceAtLeast(0)

        item {
            Box(modifier = Modifier.background(Color(0xFF141414))) {
                @Suppress("DEPRECATION")
                ScrollableTabRow(
                    selectedTabIndex = selectedSeasonIndex,
                    edgePadding = 16.dp,
                    indicator = { tabPositions ->
                        if (selectedSeasonIndex < tabPositions.size) {
                            TabRowDefaults.Indicator(
                                modifier =
                                    Modifier.tabIndicatorOffset(tabPositions[selectedSeasonIndex]),
                                color = Color.White,
                            )
                        }
                    },
                    containerColor = Color.Transparent,
                    divider = {},
                ) {
                    state.seasons.forEachIndexed { index, season ->
                        Tab(
                            selected = index == selectedSeasonIndex,
                            onClick = {
                                onAction(DetailsAction.OnSeasonSelected(season.seasonNumber))
                            },
                            text = {
                                Text(
                                    text = season.name ?: "Season ${season.seasonNumber}",
                                    fontWeight =
                                        if (index == selectedSeasonIndex) FontWeight.Bold
                                        else FontWeight.Normal,
                                    color =
                                        if (index == selectedSeasonIndex) Color.White
                                        else Color.Gray,
                                )
                            },
                        )
                    }
                }
            }
        }
    }

    if (state.episodes.isNotEmpty()) {
        items(items = state.episodes, key = { it.id }) { episode ->
            EpisodeCard(
                episode = episode,
                fallbackArtwork = listOf(media.backdropPath, media.posterPath),
                isWatched = state.watchedEpisodeIds.contains(episode.id),
                progress = state.episodeProgressMap[episode.id],
                onMarkWatched = { onAction(DetailsAction.OnMarkEpisodeWatchedToggle(it)) },
                onClick = {
                    if (episode.filePath != null) {
                        onAction(DetailsAction.OnPlayEpisode(episode.id, episode.filePath))
                    }
                },
                onLongClick = { onEpisodeLongClick(episode) },
                modifier = Modifier.padding(horizontal = 0.dp, vertical = 0.dp),
            )
        }
    } else if (
        (media.mediaType == MediaType.TV_SHOW || media.mediaType == MediaType.ANIME) &&
            state.seasons.isNotEmpty()
    ) {
        item {
            Box(
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    androidx.compose.ui.res.stringResource(
                        com.singam.lionlibrary.R.string.ui_no_episodes_found_for_this_season
                    )
                )
            }
        }
    }
}

@Composable
private fun EpisodeOptionItem(
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

private val PreviewDetailsFilm =
    com.singam.lionlibrary.presentation.components.PreviewFilm.copy(
        tmdbId = 7,
        isUnidentified = false,
        overview =
            "A long journey through familiar places, with unexpected discoveries and a new beginning.",
        duration = 130,
        rating = 8f,
        certification = "PG-13",
        letterboxdRating = 3.5f,
    )

@androidx.compose.ui.tooling.preview.Preview(name = "Movie details actions")
@Composable
private fun MovieHeaderPreview() {
    com.singam.lionlibrary.ui.theme.LionLibraryTheme {
        HeroHeaderSection(
            PreviewDetailsFilm,
            DetailsState(media = PreviewDetailsFilm, isLoading = false),
            false,
            {},
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(
    name = "Resume and saved movie",
    fontScale = 2f,
    widthDp = 360,
)
@Composable
private fun ResumeHeaderPreview() {
    com.singam.lionlibrary.ui.theme.LionLibraryTheme {
        val media = PreviewDetailsFilm.copy(isWatchlisted = true)
        HeroHeaderSection(
            media,
            DetailsState(media = media, isLoading = false, moviePositionMs = 65000),
            false,
            {},
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Watched movie")
@Composable
private fun WatchedHeaderPreview() {
    com.singam.lionlibrary.ui.theme.LionLibraryTheme {
        HeroHeaderSection(
            PreviewDetailsFilm,
            DetailsState(media = PreviewDetailsFilm, isLoading = false, isMovieWatched = true),
            false,
            {},
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Show header wide", widthDp = 840)
@Composable
private fun ShowHeaderPreview() {
    com.singam.lionlibrary.ui.theme.LionLibraryTheme {
        val media =
            PreviewDetailsFilm.copy(
                mediaType = MediaType.TV_SHOW,
                duration = null,
                letterboxdRating = null,
            )
        HeroHeaderSection(media, DetailsState(media = media, isLoading = false), true, {})
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Local file without metadata")
@Composable
private fun LocalHeaderPreview() {
    com.singam.lionlibrary.ui.theme.LionLibraryTheme {
        val media =
            com.singam.lionlibrary.presentation.components.PreviewFilm.copy(
                mediaType = MediaType.TV_SHOW,
                filePath = "content://fixture/episode",
                genres = null,
                year = null,
            )
        HeroHeaderSection(media, DetailsState(media = media, isLoading = false), false, {})
    }
}
