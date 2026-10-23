package com.singam.lionlibrary.presentation.player

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import com.singam.lionlibrary.presentation.player.engine.AspectMode
import com.singam.lionlibrary.presentation.player.engine.EnginePlaybackState
import com.singam.lionlibrary.presentation.player.engine.EngineType
import com.singam.lionlibrary.presentation.player.engine.LionPlayerEngine
import kotlin.math.roundToInt
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
            if (event == Lifecycle.Event.ON_PAUSE) {
                viewModel.onAction(PlayerAction.Pause)
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

    PlayerScreen(
        state = state,
        videoSurface = {
            androidx.compose.runtime.key(state.session) {
                PlayerVideoSurface(viewModel.engine, state.engineType)
            }
        },
        onAction = viewModel::onAction,
        onBack = onBack,
        snackbarHostState = snackbarHostState,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    state: PlayerState,
    videoSurface: @Composable () -> Unit,
    onAction: (PlayerAction) -> Unit,
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    val showControls = state.showControls
    val currentState by rememberUpdatedState(state)
    var showLeftSeekFeedback by remember { mutableStateOf(false) }
    var brightnessLevel by remember { mutableStateOf<Float?>(null) }
    var brightnessDragging by remember { mutableStateOf(false) }
    var volumeLevel by remember { mutableStateOf<Float?>(null) }
    var volumeDragging by remember { mutableStateOf(false) }

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
    var showRightSeekFeedback by remember { mutableStateOf(false) }

    LaunchedEffect(showLeftSeekFeedback) {
        if (showLeftSeekFeedback) {
            delay(500)
            showLeftSeekFeedback = false
        }
    }
    LaunchedEffect(showRightSeekFeedback) {
        if (showRightSeekFeedback) {
            delay(500)
            showRightSeekFeedback = false
        }
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
        state.currentPositionMs,
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

        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier =
                    Modifier.weight(1f)
                        .fillMaxHeight()
                        .pointerInput(state.isLocked) {
                            if (state.isLocked) return@pointerInput
                            var initialBrightness = 0f
                            detectVerticalDragGestures(
                                onDragStart = {
                                    val window = activity?.window
                                    initialBrightness = window?.attributes?.screenBrightness ?: 0.5f
                                    if (initialBrightness < 0f) {
                                        try {
                                            val sysBrightness =
                                                Settings.System.getInt(
                                                    context.contentResolver,
                                                    Settings.System.SCREEN_BRIGHTNESS,
                                                )
                                            initialBrightness = sysBrightness / 255f
                                        } catch (e: Exception) {
                                            initialBrightness = 0.5f
                                        }
                                    }
                                    brightnessDragging = true
                                    brightnessLevel = initialBrightness
                                },
                                onDragEnd = { brightnessDragging = false },
                                onDragCancel = { brightnessDragging = false },
                                onVerticalDrag = { change, dragAmount ->
                                    change.consume()
                                    val dragDelta = -dragAmount / size.height
                                    val newBrightness =
                                        (initialBrightness + dragDelta * 2).coerceIn(0f, 1f)
                                    activity?.window?.attributes =
                                        activity?.window?.attributes?.apply {
                                            screenBrightness = newBrightness
                                        }
                                    initialBrightness = newBrightness
                                    brightnessLevel = newBrightness
                                },
                            )
                        }
                        .pointerInput(state.isLocked) {
                            if (state.isLocked) return@pointerInput
                            var initialPosition = 0L
                            detectHorizontalDragGestures(
                                onDragStart = {
                                    initialPosition = currentState.currentPositionMs
                                    onAction(PlayerAction.OnControlsVisibilityChanged(true))
                                },
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    val fullWidth = size.width * 3f
                                    val dragDeltaMs =
                                        (dragAmount / fullWidth) * currentState.durationMs * 0.5f
                                    val newPosition =
                                        (initialPosition + dragDeltaMs.toLong()).coerceIn(
                                            0L,
                                            currentState.durationMs.coerceAtLeast(0),
                                        )
                                    onAction(PlayerAction.OnScrub(newPosition))
                                    initialPosition = newPosition
                                },
                                onDragEnd = { onAction(PlayerAction.OnScrubEnd) },
                                onDragCancel = { onAction(PlayerAction.OnScrubEnd) },
                            )
                        }
                        .pointerInput(state.isLocked) {
                            val locked = state.isLocked
                            detectTapGestures(
                                onDoubleTap = { _: Offset ->
                                        showLeftSeekFeedback = true
                                        onAction(PlayerAction.OnSeekBackward)
                                    }
                                        .takeUnless { locked },
                                onTap = {
                                    onAction(
                                        PlayerAction.OnControlsVisibilityChanged(
                                            !currentState.showControls
                                        )
                                    )
                                },
                            )
                        },
                contentAlignment = Alignment.Center,
            ) {
                SeekFeedback(visible = showLeftSeekFeedback, isForward = false)
            }

            Box(
                modifier =
                    Modifier.weight(1f)
                        .fillMaxHeight()
                        .pointerInput(state.isLocked) {
                            if (state.isLocked) return@pointerInput
                            var initialPosition = 0L
                            detectHorizontalDragGestures(
                                onDragStart = {
                                    initialPosition = currentState.currentPositionMs
                                    onAction(PlayerAction.OnControlsVisibilityChanged(true))
                                },
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    val fullWidth = size.width * 3f
                                    val dragDeltaMs =
                                        (dragAmount / fullWidth) * currentState.durationMs * 0.5f
                                    val newPosition =
                                        (initialPosition + dragDeltaMs.toLong()).coerceIn(
                                            0L,
                                            currentState.durationMs.coerceAtLeast(0),
                                        )
                                    onAction(PlayerAction.OnScrub(newPosition))
                                    initialPosition = newPosition
                                },
                                onDragEnd = { onAction(PlayerAction.OnScrubEnd) },
                                onDragCancel = { onAction(PlayerAction.OnScrubEnd) },
                            )
                        }
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = {
                                    onAction(
                                        PlayerAction.OnControlsVisibilityChanged(
                                            !currentState.showControls
                                        )
                                    )
                                }
                            )
                        }
            )

            Box(
                modifier =
                    Modifier.weight(1f)
                        .fillMaxHeight()
                        .pointerInput(state.isLocked) {
                            if (state.isLocked) return@pointerInput
                            val maxVolume =
                                audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                            // Fractional so slow drags still accumulate between volume steps.
                            var dragVolume = 0f
                            detectVerticalDragGestures(
                                onDragStart = {
                                    dragVolume =
                                        audioManager
                                            .getStreamVolume(AudioManager.STREAM_MUSIC)
                                            .toFloat()
                                    volumeDragging = true
                                    volumeLevel = if (maxVolume > 0) dragVolume / maxVolume else 0f
                                },
                                onDragEnd = { volumeDragging = false },
                                onDragCancel = { volumeDragging = false },
                                onVerticalDrag = { change, dragAmount ->
                                    change.consume()
                                    val dragDelta = -dragAmount / size.height
                                    dragVolume =
                                        (dragVolume + dragDelta * maxVolume * 2).coerceIn(
                                            0f,
                                            maxVolume.toFloat(),
                                        )
                                    val newVolume = dragVolume.roundToInt()
                                    if (
                                        newVolume !=
                                            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                    ) {
                                        audioManager.setStreamVolume(
                                            AudioManager.STREAM_MUSIC,
                                            newVolume,
                                            0,
                                        )
                                    }
                                    volumeLevel = if (maxVolume > 0) dragVolume / maxVolume else 0f
                                },
                            )
                        }
                        .pointerInput(state.isLocked) {
                            if (state.isLocked) return@pointerInput
                            var initialPosition = 0L
                            detectHorizontalDragGestures(
                                onDragStart = {
                                    initialPosition = currentState.currentPositionMs
                                    onAction(PlayerAction.OnControlsVisibilityChanged(true))
                                },
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    val fullWidth = size.width * 3f
                                    val dragDeltaMs =
                                        (dragAmount / fullWidth) * currentState.durationMs * 0.5f
                                    val newPosition =
                                        (initialPosition + dragDeltaMs.toLong()).coerceIn(
                                            0L,
                                            currentState.durationMs.coerceAtLeast(0),
                                        )
                                    onAction(PlayerAction.OnScrub(newPosition))
                                    initialPosition = newPosition
                                },
                                onDragEnd = { onAction(PlayerAction.OnScrubEnd) },
                                onDragCancel = { onAction(PlayerAction.OnScrubEnd) },
                            )
                        }
                        .pointerInput(state.isLocked) {
                            val locked = state.isLocked
                            detectTapGestures(
                                onDoubleTap = { _: Offset ->
                                        showRightSeekFeedback = true
                                        onAction(PlayerAction.OnSeekForward)
                                    }
                                        .takeUnless { locked },
                                onTap = {
                                    onAction(
                                        PlayerAction.OnControlsVisibilityChanged(
                                            !currentState.showControls
                                        )
                                    )
                                },
                            )
                        },
                contentAlignment = Alignment.Center,
            ) {
                SeekFeedback(visible = showRightSeekFeedback, isForward = true)
            }
        }

        val unlocked = !state.isLocked
        AnimatedVisibility(
            visible =
                !showControls &&
                    unlocked &&
                    !state.isPlaying &&
                    state.playbackState == EnginePlaybackState.READY &&
                    state.panel == PlayerPanel.NONE &&
                    state.failure == null &&
                    state.durationMs > 0,
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
            visible = showControls && unlocked && state.panel != PlayerPanel.INFO,
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
                    currentPositionMs = state.currentPositionMs,
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
                        val target =
                            (state.currentPositionMs + 90_000).coerceAtMost(state.durationMs)
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
                    modifier =
                        Modifier.align(Alignment.BottomCenter)
                            .safeDrawingPadding()
                            .padding(start = 24.dp, end = 24.dp, bottom = 20.dp),
                )
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

        GestureLevelPill(
            level = brightnessLevel,
            icon = Icons.Default.BrightnessMedium,
            contentDescription = stringResource(R.string.ui_brightness),
            modifier =
                Modifier.align(Alignment.CenterStart).safeDrawingPadding().padding(start = 32.dp),
        )
        GestureLevelPill(
            level = volumeLevel,
            icon =
                if ((volumeLevel ?: 1f) <= 0f) Icons.AutoMirrored.Filled.VolumeOff
                else Icons.AutoMirrored.Filled.VolumeUp,
            contentDescription = stringResource(R.string.ui_volume),
            modifier =
                Modifier.align(Alignment.CenterEnd).safeDrawingPadding().padding(end = 32.dp),
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
    Box(
        Modifier.fillMaxSize()
            .background(
                MaterialTheme.colorScheme.scrim.copy(
                    alpha = if (state.panel == PlayerPanel.INFO) .8f else .4f
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
                    modifier =
                        panelModifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .fillMaxWidth(.82f)
                            .widthIn(max = 880.dp),
                )
            PlayerPanel.AUDIO ->
                AudioPanel(
                    tracks = state.audioTracks,
                    onTrackSelect = { onAction(PlayerAction.SelectAudio(it)) },
                    onDismiss = dismiss,
                    modifier =
                        panelModifier
                            .align(Alignment.BottomStart)
                            .widthIn(max = 380.dp)
                            .fillMaxWidth(.5f)
                            .fillMaxHeight(.85f),
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

/** Vertical level indicator shown while a brightness or volume swipe is in progress. */
@Composable
private fun GestureLevelPill(
    level: Float?,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    // Compose-internal: keeps the last fill while the pill fades out.
    var lastLevel by remember { mutableFloatStateOf(0f) }
    if (level != null) lastLevel = level.coerceIn(0f, 1f)
    androidx.compose.animation.AnimatedVisibility(
        visible = level != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        GestureLevelIndicator(lastLevel, icon, contentDescription)
    }
}

@Composable
private fun GestureLevelIndicator(
    level: Float,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.width(44.dp),
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.scrim.copy(alpha = .6f),
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.ui_level_percent, (level * 100).roundToInt()),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Spacer(Modifier.height(8.dp))
            Box(
                modifier =
                    Modifier.width(6.dp)
                        .height(120.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.onBackground.copy(alpha = .25f)),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Box(
                    Modifier.fillMaxWidth()
                        .fillMaxHeight(level.coerceIn(0f, 1f))
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
            Spacer(Modifier.height(8.dp))
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Brightness level")
@Composable
private fun BrightnessLevelPreview() {
    com.singam.lionlibrary.ui.theme.LionLibraryTheme {
        GestureLevelIndicator(.6f, Icons.Default.BrightnessMedium, "Brightness")
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Volume full")
@Composable
private fun VolumeFullPreview() {
    com.singam.lionlibrary.ui.theme.LionLibraryTheme {
        GestureLevelIndicator(1f, Icons.AutoMirrored.Filled.VolumeUp, "Volume")
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Volume muted")
@Composable
private fun VolumeMutedPreview() {
    com.singam.lionlibrary.ui.theme.LionLibraryTheme {
        GestureLevelIndicator(0f, Icons.AutoMirrored.Filled.VolumeOff, "Volume")
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Level, large font", fontScale = 2f)
@Composable
private fun LevelLargeFontPreview() {
    com.singam.lionlibrary.ui.theme.LionLibraryTheme {
        GestureLevelIndicator(1f, Icons.Default.BrightnessMedium, "Brightness")
    }
}

@Composable
private fun SeekFeedback(visible: Boolean, isForward: Boolean) {
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector =
                    if (isForward) Icons.Default.FastForward else Icons.Default.FastRewind,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(48.dp),
            )
            Text(
                text = if (isForward) "+10s" else "-10s",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private fun PlayerVideoSurface(engine: LionPlayerEngine?, engineType: EngineType) {
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
                update = { view -> engine?.attachToView(view as android.view.ViewGroup) },
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
                update = { view -> engine?.attachToView(view as android.view.ViewGroup) },
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
            {},
            {},
            {},
        )
    }
}
