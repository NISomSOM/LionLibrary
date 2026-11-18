package com.singam.lionlibrary.presentation.details

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.singam.lionlibrary.R
import com.singam.lionlibrary.domain.model.Episode
import com.singam.lionlibrary.domain.model.MediaItem
import com.singam.lionlibrary.domain.model.MediaType
import com.singam.lionlibrary.domain.model.genreList
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
import com.singam.lionlibrary.presentation.components.LongPressPreview
import com.singam.lionlibrary.presentation.components.MediaOptionItem
import com.singam.lionlibrary.presentation.components.MediaRow
import com.singam.lionlibrary.presentation.components.PreviewFilm
import com.singam.lionlibrary.presentation.components.rememberBackdropAlignment
import com.singam.lionlibrary.presentation.components.rememberHeroPullConnection
import com.singam.lionlibrary.presentation.components.stretchedHeight
import com.singam.lionlibrary.presentation.navigation.LocalDetailsBlur
import com.singam.lionlibrary.presentation.navigation.LocalNavAnimatedVisibilityScope
import com.singam.lionlibrary.presentation.navigation.LocalPosterTapOrigin
import com.singam.lionlibrary.presentation.navigation.LocalPosterTransitions
import com.singam.lionlibrary.presentation.navigation.LocalSharedTransitionScope
import com.singam.lionlibrary.presentation.navigation.PosterTiming
import com.singam.lionlibrary.presentation.navigation.PosterTransition
import com.singam.lionlibrary.presentation.navigation.detailsPosterSharedBounds
import com.singam.lionlibrary.presentation.navigation.posterHeroHeight
import com.singam.lionlibrary.ui.theme.LionAlpha
import com.singam.lionlibrary.ui.theme.LionLayout
import com.singam.lionlibrary.ui.theme.LionLibraryTheme
import com.singam.lionlibrary.ui.theme.LionMotion
import com.singam.lionlibrary.ui.theme.LionTheme
import com.singam.lionlibrary.ui.theme.rememberSystemMotionEnabled
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@Composable
fun DetailsRoot(
    snackbarHostState: SnackbarHostState,
    windowSizeClass: WindowSizeClass,
    onNavigateToPlayer: (MediaType, Long) -> Unit,
    onBack: () -> Unit = {},
    // A related title's poster opens its own Details page.
    onMediaClick: (Long) -> Unit = {},
    // The related row's arrow: Library filtered by this title's genres.
    onRelatedGenresClick: (List<String>) -> Unit = {},
    // Set when a poster card opened this page: the hero then shares that poster with the card.
    posterTransition: PosterTransition? = null,
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
        onMediaClick = onMediaClick,
        onRelatedGenresClick = onRelatedGenresClick,
        posterTransition = posterTransition,
    )
}

@Composable
fun DetailsScreen(
    state: DetailsState,
    windowSizeClass: WindowSizeClass,
    onAction: (DetailsAction) -> Unit,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onMediaClick: (Long) -> Unit = {},
    onRelatedGenresClick: (List<String>) -> Unit = {},
    posterTransition: PosterTransition? = null,
) {
    val configuration = LocalConfiguration.current
    // Only the phone-portrait hero is a full poster; other layouts keep the plain fades.
    val posterLayout =
        configuration.orientation == Configuration.ORIENTATION_PORTRAIT &&
            configuration.smallestScreenWidthDp < 600
    val listState = rememberLazyListState()
    // Scrolled so far that more than half the poster is above the screen: going back then shrinks
    // the whole page into its card (`PageShrink`) instead of flying a poster in from off screen.
    val heroHalfPx = with(LocalDensity.current) { posterHeroHeight().toPx() } / 2f
    val scrolledAway by
        remember(listState, heroHalfPx) {
            derivedStateOf {
                listState.firstVisibleItemIndex > 0 ||
                    listState.firstVisibleItemScrollOffset > heroHalfPx
            }
        }
    val tapOrigin = LocalPosterTapOrigin.current
    // The hero shares its bounds with the card only while it could fly back: scrolled away, the
    // card is left alone so it shows as soon as Home does.
    val heroSharedBounds =
        detailsPosterSharedBounds(posterTransition?.key.takeIf { posterLayout && !scrolledAway })
    // Same conditions as the hero's shared bounds, minus the scroll: a page whose poster has
    // scrolled
    // away still came from a card and still goes back to it.
    val shared =
        posterLayout &&
            posterTransition != null &&
            LocalSharedTransitionScope.current != null &&
            LocalNavAnimatedVisibilityScope.current != null &&
            rememberSystemMotionEnabled()
    val shrinkClose =
        shared &&
            scrolledAway &&
            posterTransition?.closesToCard == true &&
            tapOrigin?.cardBoundsOf(posterTransition.key) != null
    // The navigation host reads this to keep the page solid while it shrinks.
    SideEffect { posterTransition?.key?.let { tapOrigin?.setShrinksOnClose(it, shrinkClose) } }

    val transition = LocalNavAnimatedVisibilityScope.current?.transition
    val settled =
        transition == null ||
            (transition.currentState == EnterExitState.Visible &&
                transition.targetState == EnterExitState.Visible)
    val motion = remember { PosterTiming }
    // While the poster flies out of its card, the page is only that poster on its blurred
    // backdrop: composing the whole page at the start of the flight made it stutter. The body
    // is composed while the poster is landing. Saved, so it does not wait again after the player
    // or a rotation.
    var bodyVisible by rememberSaveable { mutableStateOf(!shared) }
    LaunchedEffect(settled) { if (settled) bodyVisible = true }
    LaunchedEffect(Unit) {
        delay(motion.bodyStartMs.toLong())
        bodyVisible = true
    }
    // The hero's bottom fade grows while the poster flies out of its card and is in place when it
    // lands (a fade that arrived afterwards read as a late blur), and recedes on the way back so
    // the poster matches its card again. Driven by the navigation transition, like the flight.
    val heroFade =
        transition?.animateFloat(
            transitionSpec = {
                tween(
                    if (targetState == EnterExitState.PostExit) LionMotion.Fast
                    else LionMotion.Standard,
                    easing = LinearEasing,
                )
            },
            label = "heroFade",
        ) { state ->
            if (state == EnterExitState.Visible) 1f else 0f
        }
    // The body arrives in three groups (genres and actions; details and synopsis; seasons and
    // episodes), each fading in while sliding down a little from just under the poster, a few
    // tens of milliseconds apart. One clock for all three, started when the body appears.
    val revealClock = remember { Animatable(if (bodyVisible) REVEAL_DONE else 0f) }
    LaunchedEffect(bodyVisible) {
        if (bodyVisible && revealClock.value < REVEAL_DONE) {
            val total = motion.groupFadeMs + motion.groupStaggerMs * (BODY_GROUPS - 1)
            revealClock.animateTo(REVEAL_DONE, tween(total, easing = LinearEasing))
        }
    }
    // Going back, the whole body fades out with the same small slide up, quickly, while the poster
    // holds; then the poster flies. Driven by the navigation transition, so a back gesture scrubs
    // it. Anything but Visible counts as away: a Back pressed before the page has fully arrived is
    // reported as a return to PreEnter, not as PostExit.
    val closeFade =
        transition?.animateFloat(
            transitionSpec = { tween(motion.closeBodyMs, easing = FastOutSlowInEasing) },
            label = "bodyFadeOut",
        ) { state ->
            // A shrinking page keeps its body: the whole page goes back into the card.
            if (state == EnterExitState.Visible || shrinkClose) 1f else 0f
        }
    // Only on the way back to the card; a page coming back from the player stays put.
    val shrinkProgress =
        transition?.animateFloat(
            transitionSpec = { tween(motion.shrinkMs, easing = LionMotion.Easing) },
            label = "pageShrink",
        ) { state ->
            if (state == EnterExitState.PostExit && shrinkClose) 1f else 0f
        }
    val cornerPx = with(LocalDensity.current) { LionLayout.CardRadius.toPx() }
    val pageShrink =
        remember(shrinkProgress, posterTransition?.key, cornerPx) {
            val key = posterTransition?.key
            PageShrink(
                progress = { shrinkProgress?.value ?: 0f },
                target = {
                    // The part of the card not hidden behind a bar above its list (Library).
                    key?.let { tapOrigin?.cardBoundsOf(it) }
                        ?.let { card ->
                            val top = tapOrigin?.viewportOf(key.origin)?.top ?: card.top
                            if (top > card.top) card.copy(top = minOf(top, card.bottom)) else card
                        }
                },
                fullCard = { key?.let { tapOrigin?.cardBoundsOf(it) } },
                cornerPx = cornerPx,
            )
        }
    // Faded away, the page would still take every touch meant for Home, so it leaves then.
    val bodyGone by remember(closeFade) { derivedStateOf { (closeFade?.value ?: 1f) <= 0f } }
    val reveal: BodyReveal? =
        if (shared) {
            remember(closeFade, motion) {
                BodyReveal(motion.slide) { group ->
                    val total = motion.groupFadeMs + motion.groupStaggerMs * (BODY_GROUPS - 1)
                    val elapsed = revealClock.value / REVEAL_DONE * total
                    val local =
                        ((elapsed - group * motion.groupStaggerMs) / motion.groupFadeMs).coerceIn(
                            0f,
                            1f,
                        )
                    LionMotion.EnterEasing.transform(local) * (closeFade?.value ?: 1f)
                }
            }
        } else {
            null
        }
    val motionEnabled = rememberSystemMotionEnabled()
    val heroPull = remember { mutableFloatStateOf(0f) }
    val pageBackground =
        if (LocalDetailsBlur.current) MaterialTheme.colorScheme.background
        else LionTheme.colors.playerBackground
    Box(modifier.fillMaxSize().pageShrink(pageShrink).background(pageBackground)) {
        if (shared && posterTransition != null) {
            // The shared poster is one element for the page's whole life, drawn here between the
            // ambient backdrop and the page (which leaves the hero's place empty): there from
            // the first frame for the flight out, and still the same element for the flight back.
            DetailsAmbientBackdrop(
                posterTransition.posterPath,
                Modifier.matchParentSize(),
                fadeMillis = LionMotion.Medium,
            )
            SharedHeroPoster(
                posterPath = posterTransition.posterPath,
                contentDescription = state.media?.title,
                sharedBounds = heroSharedBounds ?: Modifier,
                listState = listState,
                pullPx = { heroPull.floatValue },
                fadeAmount = { heroFade?.value ?: 1f },
                motionEnabled = motionEnabled,
            )
        }
        if (bodyVisible && !bodyGone) {
            DetailsContent(
                state,
                windowSizeClass,
                onAction,
                onBack = onBack,
                onMediaClick = onMediaClick,
                onRelatedGenresClick = onRelatedGenresClick,
                externalHero = shared,
                listState = listState,
                heroPullState = heroPull,
                bodyReveal = reveal,
            )
        }
        if (shrinkClose && posterTransition != null) {
            pageShrink.ShrinkPoster(posterTransition.posterPath)
        }
    }
}

/**
 * The portrait hero of a page opened from a poster card: that poster, sharing its bounds with the
 * card. It sits where the list's first item leaves room for it and follows the list: it scrolls
 * away with the item, moves at half speed inside its box and grows with a pull at the top.
 */
@Composable
private fun SharedHeroPoster(
    posterPath: String,
    contentDescription: String?,
    sharedBounds: Modifier,
    listState: LazyListState,
    pullPx: () -> Float,
    fadeAmount: () -> Float,
    motionEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val baseHeight = posterHeroHeight()
    Box(
        modifier
            .fillMaxWidth()
            .layout { measurable, constraints ->
                val height = baseHeight.roundToPx() + pullPx().roundToInt()
                val placeable =
                    measurable.measure(constraints.copy(minHeight = height, maxHeight = height))
                layout(placeable.width, placeable.height) {
                    // Read here, so scrolling only moves it. Past the first item it is off screen.
                    val scrolled =
                        if (listState.firstVisibleItemIndex == 0) {
                            listState.firstVisibleItemScrollOffset
                        } else {
                            placeable.height
                        }
                    placeable.place(0, -scrolled)
                }
            }
            .then(sharedBounds)
            .clipToBounds()
    ) {
        LocalArtwork(
            paths = listOf(posterPath),
            contentDescription = contentDescription,
            modifier =
                Modifier.fillMaxSize().heroImageEffects(fadeAmount, motionEnabled) {
                    // Scaled by the fade, which recedes as the poster flies back to its card: a
                    // scrolled page would otherwise land there with the picture still shifted.
                    if (listState.firstVisibleItemIndex == 0) {
                        listState.firstVisibleItemScrollOffset * fadeAmount()
                    } else {
                        0f
                    }
                },
        )
    }
}

/**
 * The hero image fades out at its bottom edge by [fadeAmount] (0 leaves it whole), so it melts into
 * the blurred ambient page instead of ending in a visible band, and moves at half the scroll speed.
 * The image only ever moves down inside its clipped box, so no edge can show and it needs no
 * over-scale. A pull stretches the hero box itself, not this layer.
 */
private fun Modifier.heroImageEffects(
    fadeAmount: () -> Float,
    motionEnabled: Boolean,
    scrollOffset: () -> Float,
): Modifier = graphicsLayer {
    compositingStrategy = CompositingStrategy.Offscreen
}
    .drawWithContent {
        drawContent()
        val amount = fadeAmount()
        if (amount > 0f) drawRect(heroFadeMask(amount), blendMode = BlendMode.DstIn)
    }
    .graphicsLayer { if (motionEnabled) translationY = scrollOffset() * HERO_PARALLAX }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailsContent(
    state: DetailsState,
    windowSizeClass: WindowSizeClass,
    onAction: (DetailsAction) -> Unit,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onMediaClick: (Long) -> Unit = {},
    onRelatedGenresClick: (List<String>) -> Unit = {},
    // True when the caller draws the ambient backdrop and the portrait hero image itself (a page
    // opened from a poster card); the hero's place in the list is then left empty.
    externalHero: Boolean = false,
    listState: LazyListState = rememberLazyListState(),
    heroPullState: MutableFloatState = remember { mutableFloatStateOf(0f) },
    // 0..1 while the body rolls down from, or back up under, an external hero; null otherwise.
    // Staggered entrance and exit of the body under an external hero; null otherwise.
    bodyReveal: BodyReveal? = null,
) {
    val selectedEpisodeForOptions = state.selectedEpisodeForOptions
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
    // Start when real content arrives, even if the navigation fade ended during loading.
    // The remembered value stays opaque through scrolling, history updates and player return.
    // A body that rolls into view needs no fade of its own.
    val contentAlpha = remember(media.id) { Animatable(if (bodyReveal != null) 1f else 0f) }
    LaunchedEffect(media.id) {
        contentAlpha.animateTo(1f, tween(240, easing = LionMotion.Easing))
    }

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

    // Episode long press: the thumbnail lifts out of the list with the episode's name, date and
    // full description, then the same options as before.
    selectedEpisodeForOptions?.let { episode ->
        val episodeLabel =
            if (episode.episodeEndNumber != null) {
                stringResource(
                    R.string.episode_range,
                    episode.seasonNumber,
                    episode.episodeNumber,
                    episode.episodeEndNumber,
                )
            } else {
                stringResource(R.string.episode_number, episode.seasonNumber, episode.episodeNumber)
            }
        LongPressPreview(
            title = episode.title ?: "Episode ${episode.episodeNumber}",
            onDismiss = { onAction(DetailsAction.SelectOptions(null)) },
            artworkAspectRatio = 16f / 9f,
            subtitle =
                listOfNotNull(episodeLabel, episode.airDate?.ifBlank { null }).joinToString(" · "),
            description = episode.overview,
            artwork = {
                LocalArtwork(
                    paths = listOf(episode.thumbnailPath, media.backdropPath, media.posterPath),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
            },
        ) {
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
    var heroPull by heroPullState
    val pullConnection = rememberHeroPullConnection(heroPullState, stretchEnabled)

    // The full-poster hero is for phone portrait with the Poster animation setting on; everywhere
    // else (setting off, landscape, tablets) the hero is the title's banner with its logo over it.
    val bannerHero = !LocalPosterTransitions.current || isTablet || isLandscapeMode
    CompositionLocalProvider(LocalBodyReveal provides bodyReveal) {
        Box(modifier = modifier.fillMaxSize().graphicsLayer { alpha = contentAlpha.value }) {
            // Same picture as the hero (the poster in portrait), so the hero melts into its own
            // blur.
            if (!externalHero) {
                DetailsAmbientBackdrop(
                    if (isLandscapeMode || bannerHero) media.backdropPath ?: media.posterPath
                    else media.posterPath ?: media.backdropPath,
                    Modifier.matchParentSize(),
                )
            }
            if (isTwoPane) {
                Row(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        HeroHeaderSection(
                            media,
                            state.playbackLabel(),
                            isLandscapeMode,
                            onAction,
                            bannerHero = bannerHero,
                        )
                        // Two panes: related titles fill the space under the banner and synopsis.
                        RelatedMediaRow(
                            media,
                            state.relatedMedia,
                            onMediaClick,
                            onRelatedGenresClick,
                            Modifier.padding(bottom = 16.dp),
                        )
                    }

                    LazyColumn(
                        state = listState,
                        // Banner and episodes share the screen half and half.
                        modifier = Modifier.weight(1f),
                        contentPadding =
                            PaddingValues(
                                bottom = 32.dp,
                                top = if (isLandscapeMode) 16.dp else 64.dp,
                            ),
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
                            showArtwork = !externalHero,
                            bannerHero = bannerHero,
                        )
                    }
                    seasonSelectorAndEpisodes(
                        media,
                        state,
                        onAction,
                        { onAction(DetailsAction.SelectOptions(it)) },
                    )
                    // At the very bottom: after the synopsis of a movie, after every episode of a
                    // show.
                    if (state.relatedMedia.isNotEmpty()) {
                        item(key = "related", contentType = "related") {
                            RelatedMediaRow(
                                media,
                                state.relatedMedia,
                                onMediaClick,
                                onRelatedGenresClick,
                                Modifier.padding(top = 16.dp).bodyRevealGroup(2),
                            )
                        }
                    }
                }
            }

            if (!isLandscapeMode) {
                // The toolbar belongs to the body: it fades with the roll.
                Box(Modifier.graphicsLayer { alpha = bodyReveal?.progress?.invoke(0) ?: 1f }) {
                    Box(
                        Modifier.matchParentSize()
                            .graphicsLayer {
                                if (!isTwoPane) {
                                    alpha = toolbarProgress
                                    translationY =
                                        -(1f - toolbarProgress) * size.height * TOOLBAR_SLIDE
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
                                            if (!showTopBarBackground)
                                                Modifier.clearAndSetSemantics {}
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
    // False leaves the image's place empty, for a hero the caller draws behind the list.
    showArtwork: Boolean = true,
    // The title's banner with its logo and genres over it, instead of the full poster.
    bannerHero: Boolean = false,
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
                            if (bannerHero) bannerHeroHeight() else posterHeroHeight(),
                            pullPx,
                        )
            }
        Box(modifier = imageModifier.clipToBounds()) {
            // The portrait hero is the title's whole poster (which carries the title itself), the
            // same picture and shape as its card. Landscape keeps the centred 16:9 backdrop; a
            // title without a poster crops its backdrop around the subject as before.
            if (showArtwork && bannerHero) {
                BannerHeroArtwork(media, isLandscapeMode, motionEnabled, scrollOffset)
            } else if (showArtwork) {
                val posterHero = !isLandscapeMode && media.posterPath != null
                LocalArtwork(
                    paths =
                        if (isLandscapeMode) listOf(media.backdropPath, media.posterPath)
                        else listOf(media.posterPath, media.backdropPath),
                    contentDescription = media.title,
                    alignment =
                        if (isLandscapeMode || posterHero) Alignment.Center
                        else rememberBackdropAlignment(media.backdropPath),
                    modifier =
                        Modifier.fillMaxSize()
                            .heroImageEffects({ 1f }, motionEnabled, scrollOffset)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            }
        }

        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(horizontal = LionLayout.DetailGutter)
                    .padding(top = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!bannerHero && !media.genres.isNullOrBlank()) {
                Text(
                    modifier = Modifier.bodyRevealGroup(0),
                    text = media.genres.split(",").take(3).joinToString(" • ") { it.trim() },
                    style =
                        MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    textAlign = TextAlign.Center,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
            Row(
                Modifier.fillMaxWidth().bodyRevealGroup(0),
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
                modifier =
                    Modifier.fillMaxWidth()
                        .bodyRevealGroup(1)
                        .horizontalScroll(rememberScrollState()),
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
                // Compose-internal: whether the synopsis is longer than the collapsed lines. Only
                // then is there anything to show more or less of.
                var isLong by remember(media.id, media.overview) { mutableStateOf(false) }
                Column(
                    modifier = Modifier.fillMaxWidth().bodyRevealGroup(1),
                    horizontalAlignment = Alignment.Start,
                ) {
                    Text(
                        text = media.overview,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = if (isExpanded) Int.MAX_VALUE else OVERVIEW_COLLAPSED_LINES,
                        overflow = TextOverflow.Ellipsis,
                        onTextLayout = { layout ->
                            isLong =
                                if (isExpanded) layout.lineCount > OVERVIEW_COLLAPSED_LINES
                                else layout.hasVisualOverflow
                        },
                    )

                    if (isLong) {
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
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/** Height of the portrait banner hero: a third taller than the screen is wide, within limits. */
@Composable
private fun bannerHeroHeight(): Dp =
    (LocalConfiguration.current.screenWidthDp.dp * BANNER_HERO_RATIO).coerceIn(420.dp, 760.dp)

/**
 * The banner hero (everywhere except a phone held upright with Poster animation on): the title's
 * backdrop, cropped around its subject in portrait, fading out at the bottom into the blurred page,
 * with a soft scrim and the title's logo (or its name) and genres over its lower part. Fills its
 * parent, the hero box.
 */
@Composable
private fun BannerHeroArtwork(
    media: MediaItem,
    isLandscapeMode: Boolean,
    motionEnabled: Boolean,
    scrollOffset: () -> Float,
) {
    val focus = rememberBackdropAlignment(media.backdropPath ?: media.posterPath)
    LocalArtwork(
        paths = listOf(media.backdropPath, media.posterPath),
        contentDescription = media.title,
        alignment = if (isLandscapeMode) Alignment.Center else focus,
        modifier =
            Modifier.fillMaxSize()
                .heroImageEffects({ 1f }, motionEnabled, scrollOffset)
                .background(MaterialTheme.colorScheme.surfaceVariant),
    )
    Box(
        Modifier.fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    .4f to Color.Transparent,
                    .62f to LionTheme.colors.scrim.copy(alpha = .22f),
                    .8f to LionTheme.colors.scrim.copy(alpha = LionAlpha.Scrim),
                    // Ends clear: below the hero the page is the bare ambient layer.
                    1f to Color.Transparent,
                )
            )
    )
    Box(
        Modifier.fillMaxSize().padding(horizontal = LionLayout.DetailGutter).padding(bottom = 8.dp),
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
                Text(
                    text = media.genres.split(",").take(3).joinToString(" • ") { it.trim() },
                    style =
                        MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    textAlign = TextAlign.Center,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Banner hero height as a multiple of the screen width. */
private const val BANNER_HERO_RATIO = 1.33f

/** Synopsis lines shown before Show more. */
private const val OVERVIEW_COLLAPSED_LINES = 3

/**
 * Other library titles sharing a genre with [media], as a Home-style shelf. Its arrow opens Library
 * filtered by this title's genres. Nothing is drawn without related titles. A related poster opens
 * its page with a crossfade (no poster flight between two Details pages).
 */
@Composable
private fun RelatedMediaRow(
    media: MediaItem,
    related: List<MediaItem>,
    onMediaClick: (Long) -> Unit,
    onGenresClick: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (related.isEmpty()) return
    MediaRow(
        title = stringResource(R.string.related_content),
        items = related,
        onItemClick = { onMediaClick(it.id) },
        onHeaderClick = { onGenresClick(media.genreList) },
        modifier = modifier,
        gutter = LionLayout.DetailGutter,
    )
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
            // Clear, so the blurred backdrop continues behind the season tabs in every layout.
            Box(modifier = Modifier.bodyRevealGroup(2)) {
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
                modifier = Modifier.bodyRevealGroup(2),
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

/**
 * How the page body enters and leaves under an external hero: [progress] gives each group's
 * visibility (0 away, 1 in place), and a group still on its way sits up to [slide] higher, just
 * under the poster.
 */
@Stable internal class BodyReveal(val slide: Dp, val progress: (group: Int) -> Float)

private val LocalBodyReveal = staticCompositionLocalOf<BodyReveal?> { null }

/** Fades and slides this part of the body with its [group]; nothing without a [BodyReveal]. */
@Composable
private fun Modifier.bodyRevealGroup(group: Int): Modifier {
    val reveal = LocalBodyReveal.current ?: return this
    return graphicsLayer {
        val p = reveal.progress(group)
        alpha = p
        translationY = -(1f - p) * reveal.slide.toPx()
    }
}

/** Body groups: genres and actions, details and synopsis, seasons and episodes. */
private const val BODY_GROUPS = 3

/** End value of the reveal clock. */
private const val REVEAL_DONE = 1f

/** The hero image is opaque down to this fraction of its height, then fades out. */
private const val HERO_FADE_START = .8f

/** Mask for the hero's bottom edge; [amount] 0 leaves the image whole, 1 fades it out fully. */
private fun heroFadeMask(amount: Float): Brush =
    Brush.verticalGradient(
        0f to Color.Black,
        HERO_FADE_START to Color.Black,
        1f to Color.Black.copy(alpha = 1f - amount),
    )

/** Hero image moves at this fraction of the scroll speed. */
private const val HERO_PARALLAX = .5f

@Preview(name = "Long synopsis with Show more")
@Composable
private fun LongSynopsisHeaderPreview() {
    LionLibraryTheme {
        val media =
            PreviewDetailsFilm.copy(
                overview =
                    "A long journey through familiar places, with unexpected discoveries and a " +
                        "new beginning. Old friends return, a quiet town keeps its secrets and " +
                        "every road leads somewhere it should not. By the end of the season " +
                        "nothing is quite what it seemed at the start."
            )
        HeroHeaderSection(
            media,
            DetailsState(media = media, isLoading = false).playbackLabel(),
            false,
            {},
        )
    }
}

@Preview(name = "Related titles")
@Preview(name = "Related titles, large text", widthDp = 320, fontScale = 2f)
@Composable
private fun RelatedMediaRowPreview() {
    LionLibraryTheme {
        RelatedMediaRow(
            PreviewDetailsFilm,
            listOf(PreviewFilm.copy(id = 2, title = "Michael"), PreviewFilm.copy(id = 3)),
            {},
            {},
        )
    }
}
