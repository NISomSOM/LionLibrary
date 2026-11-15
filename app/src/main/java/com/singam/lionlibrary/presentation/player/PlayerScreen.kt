package com.singam.lionlibrary.presentation.player

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.PlayerView
import com.singam.lionlibrary.R
import com.singam.lionlibrary.domain.model.EngineType
import com.singam.lionlibrary.presentation.player.engine.AspectMode
import com.singam.lionlibrary.presentation.player.engine.EnginePlaybackState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

private const val PAUSE_CARD_DELAY_MS = 5_000L

fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

@Composable
fun PlayerRoot(onBack: () -> Unit, viewModel: PlayerViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Read only inside the lambda below, so ticks recompose just the seek bar and time labels.
    val position by viewModel.positionMs.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context.findActivity()
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val view = androidx.compose.ui.platform.LocalView.current

    DisposableEffect(view, state.isPlaying) {
        val previous = view.keepScreenOn
        view.keepScreenOn = state.isPlaying
        onDispose { view.keepScreenOn = previous }
    }

    // Hide system bars.
    DisposableEffect(activity) {
        val window = activity?.window
        var controller: WindowInsetsControllerCompat? = null
        if (window != null) {
            controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }

        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }

    // The brightness gesture overrides the window; give the rest of the app its own back.
    DisposableEffect(activity) {
        val window = activity?.window
        val original = window?.attributes?.screenBrightness
        onDispose {
            if (window != null && original != null) {
                window.attributes = window.attributes.apply { screenBrightness = original }
            }
        }
    }

    // Pause on background.
    DisposableEffect(lifecycleOwner, viewModel) {
        val noisy =
            object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    viewModel.onAction(PlayerAction.Pause)
                }
            }
        ContextCompat.registerReceiver(
            context,
            noisy,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> viewModel.onAction(PlayerAction.Pause)
                Lifecycle.Event.ON_STOP ->
                    viewModel.onAction(PlayerAction.OnHostVisibilityChanged(false))
                Lifecycle.Event.ON_START ->
                    viewModel.onAction(PlayerAction.OnHostVisibilityChanged(true))
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            context.unregisterReceiver(noisy)
        }
    }

    // One-time events.
    LaunchedEffect(viewModel.events) {
        viewModel.events.collect { event ->
            when (event) {
                is PlayerEvent.NavigateBack -> onBack()
                is PlayerEvent.LaunchExternalPlayer -> {
                    try {
                        context.startActivity(event.intent)
                        viewModel.onAction(PlayerAction.ExternalLaunchResult(true))
                    } catch (_: android.content.ActivityNotFoundException) {
                        viewModel.onAction(PlayerAction.ExternalLaunchResult(false))
                    } catch (_: SecurityException) {
                        viewModel.onAction(PlayerAction.ExternalLaunchResult(false))
                    }
                }
                is PlayerEvent.ShowError -> {
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            message = event.message.resolve(context),
                            duration = SnackbarDuration.Long,
                        )
                    }
                }
            }
        }
    }

    // Platform brightness/volume work lives here, not in the Screen.
    val levels =
        remember(activity) {
            AndroidPlayerLevelController(
                activity,
                context.getSystemService(Context.AUDIO_SERVICE) as AudioManager,
            )
        }
    PlayerScreen(
        state = state,
        positionMs = { position },
        videoSurface = {
            androidx.compose.runtime.key(state.session) {
                PlayerVideoSurface(state.engineType, onAttach = viewModel::attachVideoSurface)
            }
        },
        onAction = viewModel::onAction,
        onBack = onBack,
        snackbarHostState = snackbarHostState,
        levels = levels,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    state: PlayerState,
    positionMs: () -> Long,
    videoSurface: @Composable () -> Unit,
    onAction: (PlayerAction) -> Unit,
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    levels: PlayerLevelController = remember { InMemoryPlayerLevelController() },
) {
    val context = LocalContext.current

    val showControls = state.showControls
    val currentState by rememberUpdatedState(state)
    val currentOnAction by rememberUpdatedState(onAction)
    val currentPosition by rememberUpdatedState(positionMs)
    // Compose-internal gesture feedback; the values they show come from the engine/controller.
    var brightnessLevel by remember { mutableStateOf<Float?>(null) }
    var brightnessDragging by remember { mutableStateOf(false) }
    var volumeLevel by remember { mutableStateOf<Float?>(null) }
    var volumeDragging by remember { mutableStateOf(false) }
    var seekDrag by remember { mutableStateOf<SeekDragFeedback?>(null) }
    var doubleTapZone by remember { mutableStateOf<GestureZone?>(null) }
    var doubleTapCount by remember { mutableIntStateOf(0) }

    // Keep each level pill up briefly after the finger lifts.
    LaunchedEffect(brightnessDragging) {
        if (!brightnessDragging) {
            delay(700)
            brightnessLevel = null
        }
    }
    LaunchedEffect(volumeDragging) {
        if (!volumeDragging) {
            delay(700)
            volumeLevel = null
        }
    }
    // Each double tap restarts the short "-10 s / +10 s" label.
    LaunchedEffect(doubleTapCount) {
        if (doubleTapCount > 0) {
            delay(600)
            doubleTapZone = null
        }
    }

    val gestureHandler =
        remember(levels) {
            object : PlayerGestureHandler {
                private var seekStartMs = 0L
                private var dragVolume = 0f
                private var dragBrightness = 0f

                override fun onTap() =
                    currentOnAction(
                        PlayerAction.OnControlsVisibilityChanged(!currentState.showControls)
                    )

                override fun onDoubleTap(zone: GestureZone) {
                    currentOnAction(
                        if (zone == GestureZone.LEFT) PlayerAction.OnSeekBackward
                        else PlayerAction.OnSeekForward
                    )
                    doubleTapZone = zone
                    doubleTapCount++
                }

                override fun onHoldStart() = currentOnAction(PlayerAction.OnHoldSpeedChange(true))

                override fun onHoldEnd() = currentOnAction(PlayerAction.OnHoldSpeedChange(false))

                override fun onSeekDragStart() {
                    seekStartMs = currentPosition()
                    currentOnAction(PlayerAction.OnControlsVisibilityChanged(true))
                }

                override fun onSeekDrag(totalDxPx: Float, widthPx: Float) {
                    val duration = currentState.durationMs
                    val offset = PlayerGestureMath.seekOffsetMs(totalDxPx, widthPx, duration)
                    val target = PlayerGestureMath.seekTarget(seekStartMs, offset, duration)
                    currentOnAction(PlayerAction.OnScrub(target))
                    seekDrag = SeekDragFeedback(target - seekStartMs, target)
                }

                override fun onSeekDragEnd() {
                    currentOnAction(PlayerAction.OnScrubEnd)
                    seekDrag = null
                }

                override fun onLevelDrag(zone: GestureZone, deltaLevel: Float) {
                    if (zone == GestureZone.LEFT) {
                        if (!volumeDragging) dragVolume = levels.volume()
                        volumeDragging = true
                        // Fractional target so slow drags still accumulate between volume steps.
                        dragVolume = (dragVolume + deltaLevel).coerceIn(0f, 1f)
                        levels.setVolume(dragVolume)
                        volumeLevel = dragVolume
                    } else {
                        if (!brightnessDragging) dragBrightness = levels.brightness()
                        brightnessDragging = true
                        dragBrightness = (dragBrightness + deltaLevel).coerceIn(0f, 1f)
                        levels.setBrightness(dragBrightness)
                        brightnessLevel = dragBrightness
                    }
                }

                override fun onLevelDragEnd(zone: GestureZone) {
                    if (zone == GestureZone.LEFT) volumeDragging = false
                    else brightnessDragging = false
                }
            }
        }
    val density = LocalDensity.current
    val layoutDirection = androidx.compose.ui.platform.LocalLayoutDirection.current
    val systemGestures = WindowInsets.systemGestures
    val exclusion = {
        // At least 24 dp at each edge: back gestures and transient system bars live there.
        val minimum = with(density) { 24.dp.toPx() }
        GestureExclusion(
            leftPx = maxOf(systemGestures.getLeft(density, layoutDirection).toFloat(), minimum),
            rightPx = maxOf(systemGestures.getRight(density, layoutDirection).toFloat(), minimum),
            topPx = maxOf(systemGestures.getTop(density).toFloat(), minimum),
            bottomPx = maxOf(systemGestures.getBottom(density).toFloat(), minimum),
        )
    }

    // Auto-hide controls; an open panel keeps them up. Locked, the unlock button always fades.
    LaunchedEffect(showControls, state.isPlaying, state.panel, state.isLocked) {
        if (
            showControls && state.panel == PlayerPanel.NONE && (state.isPlaying || state.isLocked)
        ) {
            delay(3500)
            onAction(PlayerAction.OnControlsVisibilityChanged(false))
        }
    }

    // After 5 s paused and untouched, the controls step aside for the "You're watching" card.
    // Seeking or scrubbing changes the position and restarts the wait.
    LaunchedEffect(
        showControls,
        state.isPlaying,
        state.playbackState,
        state.panel,
        state.isLocked,
        state.seekRevision,
    ) {
        if (
            showControls &&
                !state.isPlaying &&
                !state.isLocked &&
                state.playbackState == EnginePlaybackState.READY &&
                state.panel == PlayerPanel.NONE &&
                state.failure == null &&
                state.durationMs > 0
        ) {
            delay(PAUSE_CARD_DELAY_MS)
            onAction(PlayerAction.OnControlsVisibilityChanged(false))
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        videoSurface()

        Box(
            Modifier.fillMaxSize()
                .testTag("player-gestures")
                .playerGestures(state.isLocked, exclusion, gestureHandler)
        )

        // Seek and speed feedback: shadowed text near the top, clear of the picture's centre.
        PlayerGestureFeedback(
            seekDrag = seekDrag,
            doubleTapZone = doubleTapZone,
            isHoldSpeedActive = state.isHoldSpeedActive,
            modifier =
                Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(top = 56.dp),
        )

        val unlocked = !state.isLocked
        AnimatedVisibility(
            visible = state.isPauseCardVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            PlayerPauseOverlay(
                title = state.title,
                logoPath = state.logoPath,
                subtitle = state.subtitle,
                overview = state.overview,
                modifier = Modifier.fillMaxSize(),
            )
        }

        AnimatedVisibility(
            visible =
                showControls &&
                    unlocked &&
                    state.panel != PlayerPanel.INFO &&
                    state.panel != PlayerPanel.SUBTITLES &&
                    state.panel != PlayerPanel.AUDIO,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(Modifier.fillMaxSize()) {
                // Scrims only behind the controls keep the middle of the picture clear.
                Box(
                    Modifier.fillMaxWidth()
                        .fillMaxHeight(.3f)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.scrim.copy(alpha = .7f),
                                    Color.Transparent,
                                )
                            )
                        )
                )
                Box(
                    Modifier.fillMaxWidth()
                        .fillMaxHeight(.5f)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Transparent,
                                    MaterialTheme.colorScheme.scrim.copy(alpha = .85f),
                                )
                            )
                        )
                )
                PlayerBackButton(
                    onBackClick = onBack,
                    modifier =
                        Modifier.align(Alignment.TopStart)
                            .safeDrawingPadding()
                            .padding(top = 12.dp, start = 20.dp),
                )
                PlayerTopActions(
                    isExternalLoading = state.isExternalFallbackLoading,
                    onLockClick = { onAction(PlayerAction.OnLockChange(true)) },
                    onExternalClick = { onAction(PlayerAction.OnExternalFallback) },
                    modifier =
                        Modifier.align(Alignment.TopEnd)
                            .safeDrawingPadding()
                            .padding(top = 12.dp, end = 20.dp),
                )
                PlayerCenterControls(
                    isPlaying = state.isPlaying,
                    onRewindClick = { onAction(PlayerAction.OnSeekBackward) },
                    onPlayPauseClick = { onAction(PlayerAction.OnPlayPause) },
                    onForwardClick = { onAction(PlayerAction.OnSeekForward) },
                    modifier = Modifier.align(Alignment.Center),
                )
                PlayerBottomControls(
                    title = state.title,
                    subtitle = state.subtitle,
                    currentPositionMs = positionMs,
                    durationMs = state.durationMs,
                    isEpisode = state.isEpisode,
                    hasPrevious = state.hasPrevious,
                    hasNext = state.hasNext,
                    playbackSpeed = state.playbackSpeed,
                    aspectMode = state.aspectMode,
                    onScrub = { onAction(PlayerAction.OnScrub(it)) },
                    onScrubEnd = { onAction(PlayerAction.OnScrubEnd) },
                    onPreviousClick = { onAction(PlayerAction.OnPlayPrevious) },
                    onSeekForward90Click = {
                        val target = (positionMs() + 90_000).coerceAtMost(state.durationMs)
                        onAction(PlayerAction.OnSeek(target))
                    },
                    onSubtitleClick = {
                        onAction(PlayerAction.OnPanelChange(PlayerPanel.SUBTITLES))
                    },
                    onAudioClick = { onAction(PlayerAction.OnPanelChange(PlayerPanel.AUDIO)) },
                    onSpeedClick = { onAction(PlayerAction.OnPanelChange(PlayerPanel.SPEED)) },
                    onAspectClick = { onAction(PlayerAction.OnAspectCycle) },
                    onInfoClick = { onAction(PlayerAction.OnPanelChange(PlayerPanel.INFO)) },
                    onNextClick = { onAction(PlayerAction.OnPlayNext) },
                    chapters = state.chapters,
                    modifier =
                        Modifier.align(Alignment.BottomCenter)
                            .safeDrawingPadding()
                            .padding(start = 24.dp, end = 24.dp, bottom = 12.dp),
                )
            }
        }

        // Independent of the controls so it stays while they auto-hide; it sits above the footer.
        val skipButton = state.skipButton
        AnimatedVisibility(
            visible =
                skipButton != null &&
                    unlocked &&
                    state.panel == PlayerPanel.NONE &&
                    state.failure == null &&
                    !state.isPauseCardVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier =
                Modifier.align(Alignment.BottomEnd)
                    .safeDrawingPadding()
                    .padding(end = 24.dp, bottom = if (showControls) 128.dp else 32.dp),
        ) {
            // Keep the last label while fading out after the segment ends.
            var shown by remember { mutableStateOf(skipButton) }
            if (skipButton != null) shown = skipButton
            shown?.let {
                PlayerSkipButton(it, onSkipClick = { onAction(PlayerAction.OnSkipSegment) })
            }
        }

        AnimatedVisibility(
            visible = showControls && state.isLocked,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            PlayerUnlockButton(onUnlockClick = { onAction(PlayerAction.OnLockChange(false)) })
        }

        AspectFeedback(
            mode = state.aspectMode,
            modifier =
                Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(top = 24.dp),
        )

        if (unlocked && state.panel != PlayerPanel.NONE) {
            PlayerPanelHost(state = state, onAction = onAction)
        }

        // Slim bars at the edge opposite the finger, so the hand never covers the value.
        PlayerLevelBar(
            level = brightnessLevel,
            icon = Icons.Default.BrightnessMedium,
            contentDescription = stringResource(R.string.ui_brightness),
            modifier =
                Modifier.align(Alignment.CenterStart).safeDrawingPadding().padding(start = 20.dp),
        )
        PlayerLevelBar(
            level = volumeLevel,
            icon =
                if ((volumeLevel ?: 1f) <= 0f) Icons.AutoMirrored.Filled.VolumeOff
                else Icons.AutoMirrored.Filled.VolumeUp,
            contentDescription = stringResource(R.string.ui_volume),
            modifier =
                Modifier.align(Alignment.CenterEnd).safeDrawingPadding().padding(end = 20.dp),
        )

        state.failure?.let { failure ->
            Card(Modifier.align(Alignment.Center).safeDrawingPadding().padding(24.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text(failure.resolve(context), style = MaterialTheme.typography.bodyLarge)
                    androidx.compose.foundation.layout.FlowRow {
                        TextButton(onClick = { onAction(PlayerAction.Retry) }) {
                            Text(stringResource(R.string.retry))
                        }
                        TextButton(onClick = { onAction(PlayerAction.OnExternalFallback) }) {
                            Text(stringResource(R.string.open_external))
                        }
                        TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
                    }
                }
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 140.dp),
        ) { snackbarData ->
            Snackbar(
                snackbarData = snackbarHostState.currentSnackbarData ?: snackbarData,
                containerColor = Color(0xFF2D2D2D),
                contentColor = Color.White,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            )
        }
    }
}

/** Dims the video and shows the open panel; tapping outside it closes it. */
@Composable
private fun PlayerPanelHost(state: PlayerState, onAction: (PlayerAction) -> Unit) {
    val dismiss = { onAction(PlayerAction.OnPanelChange(PlayerPanel.NONE)) }
    val scrim = MaterialTheme.colorScheme.scrim
    val isSideSheet = state.panel == PlayerPanel.SUBTITLES || state.panel == PlayerPanel.AUDIO
    Box(
        Modifier.fillMaxSize()
            .then(
                // Side sheets darken toward their edge only, leaving the video clear on the left.
                if (isSideSheet)
                    Modifier.background(
                        Brush.horizontalGradient(
                            0f to Color.Transparent,
                            .45f to scrim.copy(alpha = .35f),
                            1f to scrim.copy(alpha = .8f),
                        )
                    )
                else
                    Modifier.background(
                        scrim.copy(alpha = if (state.panel == PlayerPanel.INFO) .8f else .4f)
                    )
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = dismiss,
            )
            .safeDrawingPadding()
            .padding(16.dp)
    ) {
        // Consumes taps so a click inside a panel never reaches the dismiss scrim.
        val panelModifier =
            Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            )
        when (state.panel) {
            PlayerPanel.SUBTITLES ->
                SubtitlePanel(
                    tracks = state.subtitleTracks,
                    languageFilter = state.subtitleLanguageFilter,
                    delayMs = state.subtitleDelayMs,
                    scale = state.subtitleScale,
                    supportsDelay = state.supportsSubtitleDelay,
                    supportsScale = state.supportsSubtitleScale,
                    onLanguageFilter = { onAction(PlayerAction.OnSubtitleLanguageFilter(it)) },
                    onTrackSelect = { onAction(PlayerAction.SelectSubtitle(it)) },
                    onDelayChange = { onAction(PlayerAction.OnSubtitleDelayChange(it)) },
                    onDelayReset = { onAction(PlayerAction.OnSubtitleDelayReset) },
                    onScaleChange = { onAction(PlayerAction.OnSubtitleScaleChange(it)) },
                    onDismiss = dismiss,
                    modifier = panelModifier.align(Alignment.CenterEnd).sideSheet(),
                )
            PlayerPanel.AUDIO ->
                AudioPanel(
                    tracks = state.audioTracks,
                    onTrackSelect = { onAction(PlayerAction.SelectAudio(it)) },
                    onDismiss = dismiss,
                    modifier = panelModifier.align(Alignment.CenterEnd).sideSheet(),
                )
            PlayerPanel.SPEED ->
                SpeedPanel(
                    speed = state.playbackSpeed,
                    onSpeedSelect = { onAction(PlayerAction.OnSpeedSelect(it)) },
                    onDismiss = dismiss,
                    modifier = panelModifier.align(Alignment.BottomCenter).widthIn(max = 520.dp),
                )
            PlayerPanel.INFO ->
                state.info?.let {
                    PlayerInfoOverlay(
                        info = it,
                        onDismiss = dismiss,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    )
                }
            PlayerPanel.NONE -> Unit
        }
    }
}

/**
 * MX-style side sheet for audio and subtitles: full height on the right edge and at most 400dp
 * wide, so on a landscape phone the video stays visible beside it.
 */
private fun Modifier.sideSheet(): Modifier = fillMaxHeight().widthIn(max = 400.dp).fillMaxWidth()

/** Names the new aspect mode briefly after it changes; silent on first composition. */
@Composable
private fun AspectFeedback(mode: AspectMode, modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(false) }
    var initial by remember { mutableStateOf(true) }
    LaunchedEffect(mode) {
        if (initial) {
            initial = false
            return@LaunchedEffect
        }
        visible = true
        delay(1_200)
        visible = false
    }
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.scrim.copy(alpha = .7f),
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) {
            Text(
                stringResource(mode.label()),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private fun PlayerVideoSurface(engineType: EngineType, onAttach: (android.view.ViewGroup) -> Unit) {
    // Pick video surface by engine.
    when (engineType) {
        EngineType.EXOPLAYER -> {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        // VLC-style subtitles.
                        subtitleView?.apply {
                            setStyle(
                                androidx.media3.ui.CaptionStyleCompat(
                                    android.graphics.Color.WHITE,
                                    android.graphics.Color.TRANSPARENT,
                                    android.graphics.Color.TRANSPARENT,
                                    androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                                    android.graphics.Color.BLACK,
                                    android.graphics.Typeface.DEFAULT,
                                )
                            )
                            setBottomPaddingFraction(0.04f)
                            setApplyEmbeddedFontSizes(false)
                            setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 18f)
                        }
                        layoutParams =
                            android.view.ViewGroup.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                    }
                },
                update = { view -> onAttach(view as android.view.ViewGroup) },
                modifier = Modifier.fillMaxSize(),
            )
        }
        EngineType.LIBVLC -> {
            AndroidView(
                factory = { ctx ->
                    org.videolan.libvlc.util.VLCVideoLayout(ctx).apply {
                        layoutParams =
                            android.view.ViewGroup.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                    }
                },
                update = { view -> onAttach(view as android.view.ViewGroup) },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(
    name = "Player unavailable",
    widthDp = 760,
    heightDp = 360,
    fontScale = 2f,
)
@Composable
private fun PlayerErrorPreview() {
    com.singam.lionlibrary.ui.theme.LionLibraryTheme {
        PlayerScreen(
            PlayerState(
                failure =
                    com.singam.lionlibrary.presentation.common.UiText.Resource(
                        com.singam.lionlibrary.R.string.file_access_error
                    )
            ),
            { 0L },
            {},
            {},
            {},
        )
    }
}
