package com.singam.lionlibrary.presentation.details

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.singam.lionlibrary.R
import com.singam.lionlibrary.domain.model.Episode
import com.singam.lionlibrary.domain.model.MediaItem
import com.singam.lionlibrary.domain.model.MediaType
import com.singam.lionlibrary.domain.model.isSingleFile
import com.singam.lionlibrary.presentation.components.ArtworkTitle
import com.singam.lionlibrary.presentation.components.DeletionDialog
import com.singam.lionlibrary.presentation.components.DetailsSkeleton
import com.singam.lionlibrary.presentation.components.EpisodeCard
import com.singam.lionlibrary.presentation.components.ImdbBadge
import com.singam.lionlibrary.presentation.components.ImdbYellow
import com.singam.lionlibrary.presentation.components.LetterboxdGreen
import com.singam.lionlibrary.presentation.components.LetterboxdMark
import com.singam.lionlibrary.presentation.components.LocalArtwork
import com.singam.lionlibrary.presentation.components.MediaOptionItem
import com.singam.lionlibrary.presentation.components.PreviewFilm
import com.singam.lionlibrary.presentation.components.rememberBackdropAlignment
import com.singam.lionlibrary.presentation.navigation.rememberPosterZoom
import com.singam.lionlibrary.ui.theme.LionAlpha
import com.singam.lionlibrary.ui.theme.LionLayout
import com.singam.lionlibrary.ui.theme.LionLibraryTheme
import com.singam.lionlibrary.ui.theme.LionMotion
import com.singam.lionlibrary.ui.theme.LionTheme
import com.singam.lionlibrary.ui.theme.rememberSystemMotionEnabled
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@Composable
fun DetailsRoot(
    snackbarHostState: SnackbarHostState,
    windowSizeClass: WindowSizeClass,
    onNavigateToPlayer: (MediaType, Long) -> Unit,
    onBack: () -> Unit = {},
    viewModel: DetailsViewModel = koinViewModel(),
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
                DetailsEvent.NavigateBack -> onBack()
                is DetailsEvent.ShowMessage -> showSnackbar(event.message.resolve(context))
                is DetailsEvent.NavigateToPlayer -> {
                    onNavigateToPlayer(event.mediaType, event.mediaId)
                }
                is DetailsEvent.ShowError -> showSnackbar(event.message.resolve(context))
                is DetailsEvent.LaunchPlayer -> {
                    try {
                        context.startActivity(event.intent)
                    } catch (e: Exception) {
                        showSnackbar(resources.getString(R.string.external_launch_error))
                    }
                }
                is DetailsEvent.OpenUrl -> {
                    try {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse(event.url),
                            )
                        )
                    } catch (e: ActivityNotFoundException) {
                        showSnackbar(resources.getString(R.string.error_not_found))
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

@Composable
fun DetailsScreen(
    state: DetailsState,
    windowSizeClass: WindowSizeClass,
    onAction: (DetailsAction) -> Unit,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
) {
    // One opaque container for every state of the page (loading, error, loaded). When a poster
    // was tapped, this is what zooms out of that card, so it must exist from the first frame.
    var heroLoaded by remember { mutableStateOf(false) }
    val pageReady = !state.isLoading && (state.media == null || heroLoaded)
    val zoom = rememberPosterZoom(pageReady)
    // The outer box only draws the still picture of the previous page behind an opening zoom.
    Box(modifier.fillMaxSize().then(zoom?.backdropModifier ?: Modifier)) {
        Box(
            Modifier.fillMaxSize()
                .then(zoom?.modifier ?: Modifier)
                .background(MaterialTheme.colorScheme.background)
        ) {
            DetailsContent(
                state,
                windowSizeClass,
                onAction,
                onBack = onBack,
                instantContent = zoom != null,
                onHeroLoaded = { heroLoaded = true },
            )
            // Going back: the poster fades in over the shrinking page, framed exactly like the
            // card it is heading for, so the page lands as that poster instead of fading away.
            val posterPath = state.media?.posterPath
            if (zoom != null && zoom.isClosing && posterPath != null) {
                LocalArtwork(
                    paths = listOf(posterPath),
                    contentDescription = null,
                    modifier =
                        Modifier.fillMaxWidth().aspectRatio(2f / 3f).graphicsLayer {
                            alpha = zoom.posterAlpha()
                        },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailsContent(
    state: DetailsState,
    windowSizeClass: WindowSizeClass,
    onAction: (DetailsAction) -> Unit,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    // The page zoom reveals the finished page itself, so the loaded-content fade is skipped.
    instantContent: Boolean = false,
    onHeroLoaded: () -> Unit = {},
) {
    val selectedEpisodeForOptions = state.selectedEpisodeForOptions
    val sheetState = rememberModalBottomSheetState()
    DeletionDialog(
        state.deletion,
        { onAction(DetailsAction.ConfirmDelete) },
        { onAction(DetailsAction.DismissDelete) },
    )

    if (state.isLoading) {
        DetailsSkeleton(modifier)
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
                            ?: stringResource(R.string.file_access_error),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                return
            }
    val listState = rememberLazyListState()
    // Start when real content arrives, even if the navigation fade ended during loading.
    // The remembered value stays opaque through scrolling, history updates and player return.
    val contentAlpha = remember(media.id) { Animatable(if (instantContent) 1f else 0f) }
    LaunchedEffect(media.id) { contentAlpha.animateTo(1f, tween(240, easing = LionMotion.Easing)) }

    var toolbarHeightPx by remember { mutableIntStateOf(0) }
    var primaryActionAboveToolbar by remember(media.id) { mutableStateOf(false) }
    val showTopBarBackground by
        remember(media.id, listState) {
            derivedStateOf { listState.firstVisibleItemIndex > 0 || primaryActionAboveToolbar }
        }
    val onPrimaryActionBottomChanged: (Float) -> Unit = { bottom ->
        primaryActionAboveToolbar = toolbarHeightPx > 0 && bottom <= toolbarHeightPx
    }
    // Same trigger as before; the surface now slides down a little as it fades in (and back out).
    val toolbarProgress by
        animateFloatAsState(
            targetValue = if (showTopBarBackground) 1f else 0f,
            animationSpec =
                tween(
                    LionMotion.Standard,
                    easing =
                        if (showTopBarBackground) LionMotion.EnterEasing else LionMotion.ExitEasing,
                ),
            label = "toolbarSurface",
        )
    val toolbarSurface = MaterialTheme.colorScheme.surface
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
            containerColor = LionTheme.colors.sheet,
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
                            color = LionTheme.colors.onMedia,
                        )
                        Text(
                            text =
                                stringResource(
                                    R.string.season_number,
                                    episode.seasonNumber,
                                ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = LionTheme.colors.textSecondary,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                MediaOptionItem(
                    icon = Icons.Default.CheckCircle,
                    text = stringResource(R.string.ui_mark_as_watched_upto_here),
                    onClick = {
                        onAction(DetailsAction.SelectOptions(null))
                        onAction(DetailsAction.OnMarkWatchedUpTo(episode))
                    },
                )
                MediaOptionItem(
                    icon = Icons.Default.PlayArrow,
                    text = stringResource(R.string.ui_play_in_external_player),
                    onClick = {
                        onAction(DetailsAction.SelectOptions(null))
                        onAction(DetailsAction.OnPlayEpisodeExternal(episode))
                    },
                    iconTint = LionTheme.colors.ratingStar,
                )
                MediaOptionItem(
                    icon = Icons.Default.Delete,
                    text = stringResource(R.string.delete_from_device),
                    onClick = { onAction(DetailsAction.RequestDeleteEpisode(episode.id)) },
                    iconTint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }

    val configuration = LocalConfiguration.current
    val isTablet = configuration.smallestScreenWidthDp >= 600
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    val isLandscapeMode = !isPortrait

    val isTwoPane = isTablet && isLandscapeMode && !media.isSingleFile
    val isTabletPortrait = isTablet && isPortrait
    val isTabletPortraitMovie = isTabletPortrait && media.isSingleFile

    val motionEnabled = rememberSystemMotionEnabled()
    // Only the phone portrait hero has a fixed height that can stretch with a pull.
    val stretchEnabled = motionEnabled && !isLandscapeMode && !isTabletPortraitMovie
    // Hero parallax reads the list offset in draw; nothing recomposes while scrolling.
    val heroScrollOffset = {
        if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset.toFloat()
        else 0f
    }
    // Rubber band at the top of the list: a pull makes the hero taller by this many pixels, so
    // the page follows the finger with growing resistance while the backdrop stretches to fill
    // the gap, and a spring returns it when the finger lifts. Read in layout only.
    var heroPull by remember(media.id) { mutableFloatStateOf(0f) }
    val pullConnection =
        remember(stretchEnabled) {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (heroPull <= 0f || available.y >= 0f) return Offset.Zero
                    val used = maxOf(available.y, -heroPull)
                    heroPull += used
                    return Offset(0f, used)
                }

                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (!stretchEnabled || source != NestedScrollSource.UserInput) {
                        return Offset.Zero
                    }
                    if (available.y <= 0f) return Offset.Zero
                    // The further it is pulled, the less it gives.
                    val give = HERO_PULL_RESISTANCE * (1f - heroPull / HERO_PULL_MAX_PX)
                    heroPull = (heroPull + available.y * give).coerceAtMost(HERO_PULL_MAX_PX)
                    return Offset(0f, available.y)
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    if (heroPull > 0f)
                        animate(
                            heroPull,
                            0f,
                            animationSpec = spring(dampingRatio = .82f, stiffness = 320f),
                        ) { value, _ ->
                            heroPull = value
                        }
                    return Velocity.Zero
                }
            }
        }

    val paneColor = LionTheme.colors.pane
    Box(modifier = modifier.fillMaxSize().graphicsLayer { alpha = contentAlpha.value }) {
        DetailsAmbientBackdrop(media.backdropPath ?: media.posterPath, Modifier.matchParentSize())
        if (isTwoPane) {
            Row(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    HeroHeaderSection(
                        media,
                        state.playbackLabel(),
                        isLandscapeMode,
                        onAction,
                        onArtworkLoaded = onHeroLoaded,
                    )
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
                        seasonRowColor = paneColor,
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier =
                    Modifier.fillMaxSize().nestedScroll(pullConnection).testTag("details-list"),
                contentPadding = PaddingValues(bottom = 32.dp),
            ) {
                item {
                    val heroModifier =
                        if (isTabletPortraitMovie) Modifier.fillParentMaxHeight() else Modifier
                    HeroHeaderSection(
                        media,
                        state.playbackLabel(),
                        isLandscapeMode,
                        onAction,
                        heroModifier,
                        isTabletPortraitMovie,
                        onPrimaryActionBottomChanged,
                        scrollOffset = if (motionEnabled) heroScrollOffset else ({ 0f }),
                        pullPx = { heroPull },
                        motionEnabled = motionEnabled,
                        onArtworkLoaded = onHeroLoaded,
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
            Box {
                Box(
                    Modifier.matchParentSize()
                        .graphicsLayer {
                            if (!isTwoPane) {
                                alpha = toolbarProgress
                                translationY = -(1f - toolbarProgress) * size.height * TOOLBAR_SLIDE
                            }
                        }
                        .background(if (isTwoPane) LionTheme.colors.pane else toolbarSurface)
                )
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
                                contentDescription = stringResource(R.string.back),
                                tint = LionTheme.colors.onMedia,
                            )
                        }
                    },
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            titleContentColor = LionTheme.colors.onMedia,
                        ),
                )
            }
        } else {
            IconButton(onClick = onBack, modifier = Modifier.padding(16.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = LionTheme.colors.onMedia,
                    modifier =
                        Modifier.size(36.dp)
                            .background(
                                LionTheme.colors.scrim.copy(alpha = LionAlpha.ScrimStrong),
                                RoundedCornerShape(18.dp),
                            )
                            .padding(6.dp),
                )
            }
        }
    }
}

@Composable
private fun HeroHeaderSection(
    media: MediaItem,
    @StringRes primaryActionLabel: Int,
    isLandscapeMode: Boolean,
    onAction: (DetailsAction) -> Unit,
    modifier: Modifier = Modifier,
    isTabletPortraitMovie: Boolean = false,
    onPrimaryActionBottomChanged: (Float) -> Unit = {},
    scrollOffset: () -> Float = { 0f },
    pullPx: () -> Float = { 0f },
    motionEnabled: Boolean = false,
    onArtworkLoaded: () -> Unit = {},
) {
    val haptics = LocalHapticFeedback.current
    Column(modifier = modifier.fillMaxWidth()) {
        val imageModifier =
            if (isTabletPortraitMovie) {
                Modifier.fillMaxWidth().weight(1f)
            } else {
                if (isLandscapeMode) Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                else
                    Modifier.fillMaxWidth()
                        .stretchedHeight(
                            (LocalConfiguration.current.screenWidthDp.dp * 1.33f).coerceIn(
                                420.dp,
                                760.dp,
                            ),
                            pullPx,
                        )
            }
        Box(modifier = imageModifier.clipToBounds()) {
            // Portrait heroes crop a wide backdrop; landscape keeps the centred 16:9 frame.
            val focus = rememberBackdropAlignment(media.backdropPath ?: media.posterPath)
            LocalArtwork(
                paths = listOf(media.backdropPath, media.posterPath),
                contentDescription = media.title,
                alignment = if (isLandscapeMode) Alignment.Center else focus,
                onLoaded = onArtworkLoaded,
                modifier =
                    Modifier.fillMaxSize()
                        // The image itself fades out at the bottom, so it melts into the blurred
                        // ambient page instead of ending in a visible band.
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            drawRect(HeroFadeMask, blendMode = BlendMode.DstIn)
                        }
                        .graphicsLayer {
                            if (!motionEnabled) return@graphicsLayer
                            // Parallax: half scroll speed, with a slight over-scale so edges
                            // never show. A pull stretches the hero box itself, not this layer.
                            translationY = scrollOffset() * HERO_PARALLAX
                            scaleX = HERO_OVERSCALE
                            scaleY = HERO_OVERSCALE
                            transformOrigin = TransformOrigin(.5f, 0f)
                        }
                        .background(MaterialTheme.colorScheme.surfaceVariant),
            )

            Box(
                modifier =
                    Modifier.fillMaxSize()
                        .background(
                            // Multi-stop fade that ends in the ambient page tint, so the hero runs
                            // into the blurred backdrop without a seam.
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                .4f to Color.Transparent,
                                .62f to LionTheme.colors.scrim.copy(alpha = .22f),
                                .8f to LionTheme.colors.scrim.copy(alpha = LionAlpha.Scrim),
                                // Ends clear: below the hero the page is the bare ambient layer,
                                // so any tint left here would show as a step.
                                1f to Color.Transparent,
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
                            .height(80.dp * LocalDensity.current.fontScale.coerceIn(1f, 1.6f)),
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
                            textAlign = TextAlign.Center,
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
                        contentDescription = stringResource(R.string.play),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    val buttonText = stringResource(primaryActionLabel)
                    Text(
                        buttonText,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                OutlinedIconButton(
                    onClick = {
                        haptics.performHapticFeedback(
                            if (media.isWatchlisted) HapticFeedbackType.ToggleOff
                            else HapticFeedbackType.ToggleOn
                        )
                        onAction(DetailsAction.ToggleWatchlist)
                    },
                    modifier = Modifier.size(48.dp),
                    border =
                        BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outline,
                        ),
                ) {
                    Icon(
                        if (media.isWatchlisted) Icons.Filled.Bookmark
                        else Icons.Outlined.BookmarkBorder,
                        modifier = Modifier.size(LionLayout.BookmarkGlyph),
                        contentDescription =
                            stringResource(
                                if (media.isWatchlisted) R.string.remove_watchlist
                                else R.string.add_watchlist
                            ),
                        tint =
                            if (media.isWatchlisted) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onBackground,
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // One line, Nuvio-style: year and runtime, rating box, then the rating marks.
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
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
                        "$yearText • $durationText"
                    else if (yearText.isNotEmpty()) yearText else durationText

                if (combinedText.isNotEmpty()) {
                    Text(
                        text = combinedText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                    )
                }

                if (!media.certification.isNullOrBlank()) {
                    Box(
                        modifier =
                            Modifier.border(
                                    1.dp,
                                    LionTheme.colors.textSecondary,
                                    RoundedCornerShape(4.dp),
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = media.certification,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }

                if (media.rating != null && media.rating > 0.0) {
                    val ratingDescription =
                        stringResource(
                            R.string.hero_score_description,
                            media.rating,
                        )
                    Row(
                        modifier =
                            Modifier.semantics(mergeDescendants = true) {
                                contentDescription = ratingDescription
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        ImdbBadge()
                        Text(
                            text =
                                stringResource(
                                    R.string.rating_value,
                                    media.rating,
                                ),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = ImdbYellow,
                            maxLines = 1,
                        )
                    }
                }

                if (media.isSingleFile && media.tmdbId != null) {
                    val letterboxdLabel = stringResource(R.string.ui_letterboxd)
                    TextButton(
                        onClick = { onAction(DetailsAction.OnOpenLetterboxd) },
                        modifier = Modifier.semantics { contentDescription = letterboxdLabel },
                        contentPadding = PaddingValues(horizontal = 4.dp),
                    ) {
                        LetterboxdMark()
                        media.letterboxdRating?.let { lb ->
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text =
                                    stringResource(
                                        R.string.rating_value,
                                        lb,
                                    ),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = LetterboxdGreen,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (!media.overview.isNullOrBlank()) {
                var isExpanded by
                    rememberSaveable(media.id) {
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

                    TextButton(
                        onClick = { isExpanded = !isExpanded },
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Text(
                            stringResource(
                                if (isExpanded) R.string.show_less else R.string.show_more
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
    // Clear by default so the blurred backdrop continues behind the season tabs; the two-pane
    // layout passes its fixed pane colour.
    seasonRowColor: Color = Color.Transparent,
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
            Box(modifier = Modifier.background(seasonRowColor)) {
                SecondaryScrollableTabRow(
                    selectedTabIndex = selectedSeasonIndex,
                    edgePadding = 16.dp,
                    indicator = {
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(selectedSeasonIndex),
                            color = LionTheme.colors.onMedia,
                        )
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
                                    text =
                                        season.name
                                            ?: stringResource(
                                                R.string.season_number,
                                                season.seasonNumber,
                                            ),
                                    fontWeight =
                                        if (index == selectedSeasonIndex) FontWeight.Bold
                                        else FontWeight.Normal,
                                    color =
                                        if (index == selectedSeasonIndex) LionTheme.colors.onMedia
                                        else LionTheme.colors.textSecondary,
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
                Text(stringResource(R.string.ui_no_episodes_found_for_this_season))
            }
        }
    }
}

private val PreviewDetailsFilm =
    PreviewFilm.copy(
        tmdbId = 7,
        isUnidentified = false,
        overview =
            "A long journey through familiar places, with unexpected discoveries and a new beginning.",
        duration = 130,
        rating = 8f,
        certification = "PG-13",
        letterboxdRating = 3.5f,
    )

@Preview(name = "Movie details actions")
@Composable
private fun MovieHeaderPreview() {
    LionLibraryTheme {
        HeroHeaderSection(
            PreviewDetailsFilm,
            DetailsState(media = PreviewDetailsFilm, isLoading = false).playbackLabel(),
            false,
            {},
        )
    }
}

@Preview(
    name = "Resume and saved movie",
    fontScale = 2f,
    widthDp = 360,
)
@Composable
private fun ResumeHeaderPreview() {
    LionLibraryTheme {
        val media = PreviewDetailsFilm.copy(isWatchlisted = true)
        HeroHeaderSection(
            media,
            DetailsState(media = media, isLoading = false, moviePositionMs = 65000).playbackLabel(),
            false,
            {},
        )
    }
}

@Preview(name = "Watched movie")
@Composable
private fun WatchedHeaderPreview() {
    LionLibraryTheme {
        HeroHeaderSection(
            PreviewDetailsFilm,
            DetailsState(media = PreviewDetailsFilm, isLoading = false, isMovieWatched = true)
                .playbackLabel(),
            false,
            {},
        )
    }
}

@Preview(name = "Show header wide", widthDp = 840)
@Composable
private fun ShowHeaderPreview() {
    LionLibraryTheme {
        val media =
            PreviewDetailsFilm.copy(
                mediaType = MediaType.TV_SHOW,
                duration = null,
                letterboxdRating = null,
            )
        HeroHeaderSection(
            media,
            DetailsState(media = media, isLoading = false).playbackLabel(),
            true,
            {},
        )
    }
}

@Preview(name = "Local file without metadata")
@Composable
private fun LocalHeaderPreview() {
    LionLibraryTheme {
        val media =
            PreviewFilm.copy(
                mediaType = MediaType.TV_SHOW,
                filePath = "content://fixture/episode",
                genres = null,
                year = null,
            )
        HeroHeaderSection(
            media,
            DetailsState(media = media, isLoading = false).playbackLabel(),
            false,
            {},
        )
    }
}

/** Fraction of the toolbar height its surface travels while sliding in. */
private const val TOOLBAR_SLIDE = .35f

/** Opaque top, fading to transparent over the lower third of the hero image. */
private val HeroFadeMask =
    Brush.verticalGradient(0f to Color.Black, .62f to Color.Black, 1f to Color.Transparent)

/** Hero image moves at this fraction of the scroll speed. */
private const val HERO_PARALLAX = .5f

/** Slight over-scale so parallax never reveals the image edge. */
private const val HERO_OVERSCALE = 1.06f

/** Pull distance kept per pixel dragged past the top (before it tightens), and its cap. */
private const val HERO_PULL_RESISTANCE = .55f
private const val HERO_PULL_MAX_PX = 420f

/** Fixed hero height plus the current pull, applied in layout so a pull never recomposes. */
private fun Modifier.stretchedHeight(base: Dp, pullPx: () -> Float): Modifier =
    layout { measurable, constraints ->
        val height = base.roundToPx() + pullPx().roundToInt()
        val placeable = measurable.measure(constraints.copy(minHeight = height, maxHeight = height))
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
