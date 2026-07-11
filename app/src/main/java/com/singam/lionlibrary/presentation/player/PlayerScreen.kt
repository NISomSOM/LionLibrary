package com.singam.lionlibrary.presentation.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.provider.Settings
import androidx.activity.compose.LocalActivity
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.PlayerView
import androidx.navigation.NavHostController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun PlayerRoot(
    navController: NavHostController,
    viewModel: PlayerViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context.findActivity()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Handle Orientation and System Bars
    DisposableEffect(activity) {
        val originalOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        val window = activity?.window
        var controller: WindowInsetsControllerCompat? = null
        if (window != null) {
            controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        onDispose {
            activity?.requestedOrientation = originalOrientation
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // Handle Lifecycle for pausing
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                if (state.isPlaying) {
                    viewModel.onAction(PlayerAction.OnPlayPause)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Handle Events
    LaunchedEffect(viewModel.events) {
        viewModel.events.collect { event ->
            when (event) {
                is PlayerEvent.NavigateBack -> navController.popBackStack()
                is PlayerEvent.LaunchExternalPlayer -> {
                    try {
                        context.startActivity(event.intent)
                    } catch (e: Exception) {
                        // ignore or toast
                    }
                }
                is PlayerEvent.ShowError -> {
                    // toast or something, but player is fullscreen
                }
            }
        }
    }

    PlayerScreen(
        state = state,
        player = viewModel.player,
        onAction = viewModel::onAction,
        onBack = { navController.popBackStack() }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    state: PlayerState,
    player: androidx.media3.common.Player,
    onAction: (PlayerAction) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    
    var showControls by remember { mutableStateOf(true) }
    var showTracksSheet by remember { mutableStateOf(false) }
    
    // Auto-hide controls
    LaunchedEffect(showControls, state.isPlaying) {
        if (showControls && state.isPlaying) {
            delay(3500)
            showControls = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // ExoPlayer Surface
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = false
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Gesture Overlay
        Row(modifier = Modifier.fillMaxSize()) {
            // Left Third - Brightness & Seek Backward
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .pointerInput(Unit) {
                        var initialBrightness = 0f
                        detectVerticalDragGestures(
                            onDragStart = {
                                val window = activity?.window
                                initialBrightness = window?.attributes?.screenBrightness ?: 0.5f
                                if (initialBrightness < 0f) {
                                    try {
                                        val sysBrightness = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
                                        initialBrightness = sysBrightness / 255f
                                    } catch (e: Exception) {
                                        initialBrightness = 0.5f
                                    }
                                }
                            },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                val dragDelta = -dragAmount / size.height // up is positive
                                val newBrightness = (initialBrightness + dragDelta * 2).coerceIn(0f, 1f)
                                activity?.window?.attributes = activity?.window?.attributes?.apply {
                                    screenBrightness = newBrightness
                                }
                                initialBrightness = newBrightness
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = { onAction(PlayerAction.OnSeekBackward) },
                            onTap = { showControls = !showControls }
                        )
                    }
            )

            // Middle Third - Just Tap for Controls & Scrubbing
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { showControls = !showControls })
                    }
            )

            // Right Third - Volume & Seek Forward
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .pointerInput(Unit) {
                        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        var initialVolume = 0
                        detectVerticalDragGestures(
                            onDragStart = {
                                initialVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                            },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                val dragDelta = -dragAmount / size.height
                                val volumeChange = (dragDelta * maxVolume * 2).toInt()
                                val newVolume = (initialVolume + volumeChange).coerceIn(0, maxVolume)
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = { onAction(PlayerAction.OnSeekForward) },
                            onTap = { showControls = !showControls }
                        )
                    }
            )
        }

        // Horizontal Drag for Scrubbing (Full Screen Width)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    var initialPosition = 0L
                    detectHorizontalDragGestures(
                        onDragStart = { 
                            initialPosition = state.currentPositionMs
                            showControls = true
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            val dragDeltaMs = (dragAmount / size.width) * state.durationMs * 0.5f // Scrub speed
                            val newPosition = (initialPosition + dragDeltaMs.toLong()).coerceIn(0L, state.durationMs)
                            onAction(PlayerAction.OnScrub(newPosition))
                            initialPosition = newPosition
                        },
                        onDragEnd = { onAction(PlayerAction.OnScrubEnd) },
                        onDragCancel = { onAction(PlayerAction.OnScrubEnd) }
                    )
                }
        )

        // Controls Overlay
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                // Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = state.title, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (state.subtitle.isNotBlank()) {
                            Text(text = state.subtitle, color = Color.LightGray, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    
                    if (state.isExternalFallbackLoading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    } else {
                        IconButton(onClick = { onAction(PlayerAction.OnExternalFallback) }) {
                            Icon(Icons.Default.OpenInNew, contentDescription = "Open External", tint = Color.White)
                        }
                    }
                    
                    // Tracks Button (Not fully implemented yet, but UI is there)
                    IconButton(onClick = { showTracksSheet = true }) {
                        Icon(Icons.Default.Subtitles, contentDescription = "Tracks", tint = Color.White)
                    }
                }

                // Center Controls
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(32.dp)
                ) {
                    if (state.hasPrevious) {
                        IconButton(onClick = { onAction(PlayerAction.OnPlayPrevious) }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", tint = Color.White, modifier = Modifier.fillMaxSize())
                        }
                    }
                    
                    IconButton(onClick = { onAction(PlayerAction.OnSeekBackward) }, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.Default.Replay10, contentDescription = "Rewind 10s", tint = Color.White, modifier = Modifier.fillMaxSize())
                    }

                    IconButton(onClick = { onAction(PlayerAction.OnPlayPause) }, modifier = Modifier.size(80.dp)) {
                        Icon(
                            imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (state.isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    IconButton(onClick = { onAction(PlayerAction.OnSeekForward) }, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.Default.Forward10, contentDescription = "Forward 10s", tint = Color.White, modifier = Modifier.fillMaxSize())
                    }
                    
                    if (state.hasNext) {
                        IconButton(onClick = { onAction(PlayerAction.OnPlayNext) }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.fillMaxSize())
                        }
                    }
                }

                // Bottom Bar (Seek + Skip 90s)
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { onAction(PlayerAction.OnSeek(state.currentPositionMs + 90_000)) }) {
                            Text("+90s", color = Color.White)
                        }
                    }
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = formatTime(state.currentPositionMs), color = Color.White, style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.width(8.dp))
                        Slider(
                            value = if (state.durationMs > 0) state.currentPositionMs.toFloat() / state.durationMs else 0f,
                            onValueChange = { percent ->
                                onAction(PlayerAction.OnScrub((percent * state.durationMs).toLong()))
                            },
                            onValueChangeFinished = {
                                onAction(PlayerAction.OnScrubEnd)
                            },
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = formatTime(state.durationMs), color = Color.White, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        // Resume Prompt
        if (state.showResumePrompt) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Resume from ${formatTime(state.resumePositionMs)}?", color = Color.White, style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Button(onClick = { onAction(PlayerAction.OnDismissResumePrompt) }, colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)) {
                            Text("Start Over", color = Color.White)
                        }
                        Button(onClick = { onAction(PlayerAction.OnResumePlayback) }) {
                            Text("Resume", color = Color.White)
                        }
                    }
                }
            }
        }
    }

    if (showTracksSheet) {
        ModalBottomSheet(
            onDismissRequest = { showTracksSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Tracks", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Track selection will be fully implemented in a future update.", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    if (ms < 0) return "00:00"
    val totalSeconds = ms / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
