package com.singam.lionlibrary.presentation.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Language
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import com.singam.lionlibrary.ui.theme.OrangeAccent
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
import com.singam.lionlibrary.presentation.player.engine.EngineType
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
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Configure system UI visibility
    DisposableEffect(activity) {
        val window = activity?.window
        var controller: WindowInsetsControllerCompat? = null
        if (window != null) {
            controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }

        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // Pause playback on app background
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

    // Process one-time events
    LaunchedEffect(viewModel.events) {
        viewModel.events.collect { event ->
            when (event) {
                is PlayerEvent.NavigateBack -> navController.popBackStack()
                is PlayerEvent.LaunchExternalPlayer -> {
                    try {
                        context.startActivity(event.intent)
                    } catch (e: Exception) {
                        // Suppressed intentional exception
                    }
                }
                is PlayerEvent.ShowError -> {
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            message = event.message,
                            duration = SnackbarDuration.Long
                        )
                    }
                }
            }
        }
    }

    PlayerScreen(
        state = state,
        viewModel = viewModel,
        onAction = viewModel::onAction,
        onBack = { navController.popBackStack() },
        snackbarHostState = snackbarHostState
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    state: PlayerState,
    viewModel: PlayerViewModel,
    onAction: (PlayerAction) -> Unit,
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    val engine = viewModel.engine
    val context = LocalContext.current
    val activity = context.findActivity()
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    
    var showControls by remember { mutableStateOf(true) }
    var showAudioSheet by remember { mutableStateOf(false) }
    var showSubtitleSheet by remember { mutableStateOf(false) }
    
    var showLeftSeekFeedback by remember { mutableStateOf(false) }
    var showRightSeekFeedback by remember { mutableStateOf(false) }

    LaunchedEffect(showLeftSeekFeedback) {
        if (showLeftSeekFeedback) { delay(500); showLeftSeekFeedback = false }
    }
    LaunchedEffect(showRightSeekFeedback) {
        if (showRightSeekFeedback) { delay(500); showRightSeekFeedback = false }
    }
    
    // Hide controls after inactivity
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
        // Branch video surface by engine type. Each needs a specific native View.
        when (state.engineType) {
            EngineType.EXOPLAYER -> {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            // Configure subtitle style to match VLC defaults (white, black outline, no box).
                            subtitleView?.apply {
                                setStyle(
                                    androidx.media3.ui.CaptionStyleCompat(
                                        android.graphics.Color.WHITE,
                                        android.graphics.Color.TRANSPARENT,
                                        android.graphics.Color.TRANSPARENT,
                                        androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                                        android.graphics.Color.BLACK,
                                        android.graphics.Typeface.createFromFile(
                                            "/system/fonts/Roboto-Regular.ttf"
                                        )
                                    )
                                )
                                setBottomPaddingFraction(0.04f)
                                setApplyEmbeddedFontSizes(false)
                                setFixedTextSize(
                                    android.util.TypedValue.COMPLEX_UNIT_SP,
                                    18f
                                )
                            }
                            layoutParams = android.view.ViewGroup.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                    },
                    update = { view ->
                        engine?.attachToView(view as android.view.ViewGroup)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            EngineType.LIBVLC -> {
                AndroidView(
                    factory = { ctx ->
                        org.videolan.libvlc.util.VLCVideoLayout(ctx).apply {
                            layoutParams = android.view.ViewGroup.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                    },
                    update = { view ->
                        engine?.attachToView(view as android.view.ViewGroup)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Render touch gesture zones
        Row(modifier = Modifier.fillMaxSize()) {
            // Render brightness and rewind zone
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
                                val dragDelta = -dragAmount / size.height // Map upward drag to positive
                                val newBrightness = (initialBrightness + dragDelta * 2).coerceIn(0f, 1f)
                                activity?.window?.attributes = activity?.window?.attributes?.apply {
                                    screenBrightness = newBrightness
                                }
                                initialBrightness = newBrightness
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        var initialPosition = 0L
                        detectHorizontalDragGestures(
                            onDragStart = { 
                                initialPosition = state.currentPositionMs
                                showControls = true
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                val fullWidth = size.width * 3f
                                val dragDeltaMs = (dragAmount / fullWidth) * state.durationMs * 0.5f
                                val newPosition = (initialPosition + dragDeltaMs.toLong()).coerceIn(0L, state.durationMs)
                                onAction(PlayerAction.OnScrub(newPosition))
                                initialPosition = newPosition
                            },
                            onDragEnd = { onAction(PlayerAction.OnScrubEnd) },
                            onDragCancel = { onAction(PlayerAction.OnScrubEnd) }
                        )
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = { 
                                showLeftSeekFeedback = true
                                onAction(PlayerAction.OnSeekBackward) 
                            },
                            onTap = { showControls = !showControls }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                SeekFeedback(visible = showLeftSeekFeedback, isForward = false)
            }

            // Render center zone for toggle and scrubbing
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .pointerInput(Unit) {
                        var initialPosition = 0L
                        detectHorizontalDragGestures(
                            onDragStart = { 
                                initialPosition = state.currentPositionMs
                                showControls = true
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                val fullWidth = size.width * 3f
                                val dragDeltaMs = (dragAmount / fullWidth) * state.durationMs * 0.5f
                                val newPosition = (initialPosition + dragDeltaMs.toLong()).coerceIn(0L, state.durationMs)
                                onAction(PlayerAction.OnScrub(newPosition))
                                initialPosition = newPosition
                            },
                            onDragEnd = { onAction(PlayerAction.OnScrubEnd) },
                            onDragCancel = { onAction(PlayerAction.OnScrubEnd) }
                        )
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { showControls = !showControls })
                    }
            )

            // Render volume and fast-forward zone
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
                        var initialPosition = 0L
                        detectHorizontalDragGestures(
                            onDragStart = { 
                                initialPosition = state.currentPositionMs
                                showControls = true
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                val fullWidth = size.width * 3f
                                val dragDeltaMs = (dragAmount / fullWidth) * state.durationMs * 0.5f
                                val newPosition = (initialPosition + dragDeltaMs.toLong()).coerceIn(0L, state.durationMs)
                                onAction(PlayerAction.OnScrub(newPosition))
                                initialPosition = newPosition
                            },
                            onDragEnd = { onAction(PlayerAction.OnScrubEnd) },
                            onDragCancel = { onAction(PlayerAction.OnScrubEnd) }
                        )
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = { 
                                showRightSeekFeedback = true
                                onAction(PlayerAction.OnSeekForward) 
                            },
                            onTap = { showControls = !showControls }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                SeekFeedback(visible = showRightSeekFeedback, isForward = true)
            }
        }

        // Render media controls
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
                // Render top control bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 32.dp, top = 24.dp, end = 32.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color(0x80000000), CircleShape)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = state.title, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        if (state.subtitle.isNotBlank()) {
                            Text(text = state.subtitle, color = Color.LightGray, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    
                    if (state.isExternalFallbackLoading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(48.dp).padding(12.dp))
                    } else {
                        IconButton(
                            onClick = { onAction(PlayerAction.OnExternalFallback) },
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color(0x80000000), CircleShape)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open External", tint = Color.White)
                        }
                    }
                }

                // Render center playback controls
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(48.dp)
                ) {
                    IconButton(
                        onClick = { onAction(PlayerAction.OnSeekBackward) }, 
                        modifier = Modifier.size(64.dp)
                    ) {
                        Icon(Icons.Default.Replay10, contentDescription = "Rewind 10s", tint = Color.White, modifier = Modifier.fillMaxSize())
                    }

                    IconButton(
                        onClick = { onAction(PlayerAction.OnPlayPause) }, 
                        modifier = Modifier.size(96.dp)
                    ) {
                        Icon(
                            imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (state.isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    IconButton(
                        onClick = { onAction(PlayerAction.OnSeekForward) }, 
                        modifier = Modifier.size(64.dp)
                    ) {
                        Icon(Icons.Default.Forward10, contentDescription = "Forward 10s", tint = Color.White, modifier = Modifier.fillMaxSize())
                    }
                }

                // Render bottom controls
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp, vertical = 24.dp)
                ) {
                    // Render scrub bar
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = formatTime(state.currentPositionMs), color = Color.White, style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.width(16.dp))
                        Slider(
                            value = if (state.durationMs > 0) state.currentPositionMs.toFloat() / state.durationMs else 0f,
                            onValueChange = { percent ->
                                onAction(PlayerAction.OnScrub((percent * state.durationMs).toLong()))
                            },
                            onValueChangeFinished = {
                                onAction(PlayerAction.OnScrubEnd)
                            },
                            modifier = Modifier.weight(1f).height(24.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = OrangeAccent,
                                activeTrackColor = OrangeAccent,
                                inactiveTrackColor = Color(0xFF3B3346)
                            )
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(text = formatTime(state.durationMs), color = Color.White, style = MaterialTheme.typography.labelMedium)
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Render track and engine selectors
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Row(
                            modifier = Modifier
                                .background(Color(0xFF0F0F0F), androidx.compose.foundation.shape.RoundedCornerShape(50))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { 
                                val target = (state.currentPositionMs + 90_000).coerceAtMost(state.durationMs)
                                onAction(PlayerAction.OnSeek(target)) 
                            }) {
                                Text("+90s", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            TextButton(onClick = { showSubtitleSheet = true }) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Subtitles, contentDescription = "Subs", tint = Color.White, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Subs", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            TextButton(onClick = { showAudioSheet = true }) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Audiotrack, contentDescription = "Audio", tint = Color.White, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Audio", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Render error snackbar above controls
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 140.dp) // Offset above bottom bar
        ) { snackbarData ->
            Snackbar(
                snackbarData = snackbarData,
                containerColor = Color(0xFF2D2D2D),
                contentColor = Color.White,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
            )
        }

    }

    if (showAudioSheet) {
        val audioTracks = viewModel.getAudioTracks()
        ModalBottomSheet(
            onDismissRequest = { showAudioSheet = false },
            containerColor = Color(0xFF19181A),
            contentWindowInsets = @Composable { androidx.compose.foundation.layout.WindowInsets(0) },
            dragHandle = { BottomSheetDefaults.DragHandle(modifier = Modifier.padding(top = 8.dp, bottom = 0.dp)) }
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .padding(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text(
                        "Audio Tracks",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 0.dp)
                    )
                }
                items(audioTracks) { track ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                            .background(if (track.isSelected) Color(0xFF4A3A22) else Color(0xFF262524))
                            .clickable {
                                viewModel.selectAudioTrack(track.id)
                                showAudioSheet = false
                            }
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = track.label,
                            modifier = Modifier.weight(1f),
                            color = Color.White,
                            fontWeight = if (track.isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }

    if (showSubtitleSheet) {
        val subtitleTracks = viewModel.getSubtitleTracks()
        val isSubtitlesOff = subtitleTracks.none { it.isSelected }
        ModalBottomSheet(
            onDismissRequest = { showSubtitleSheet = false },
            containerColor = Color(0xFF19181A),
            contentWindowInsets = @Composable { androidx.compose.foundation.layout.WindowInsets(0) },
            dragHandle = { BottomSheetDefaults.DragHandle(modifier = Modifier.padding(top = 8.dp, bottom = 0.dp)) }
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .padding(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text(
                        "Subtitles",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 0.dp)
                    )
                }

                // Render 'Off' toggle
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                            .background(if (isSubtitlesOff) Color(0xFF4A3A22) else Color(0xFF262524))
                            .clickable {
                                viewModel.selectSubtitleTrack(null)
                                showSubtitleSheet = false
                            }
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Off",
                            modifier = Modifier.weight(1f),
                            color = Color.White,
                            fontWeight = if (isSubtitlesOff) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }

                items(subtitleTracks) { track ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                            .background(if (track.isSelected) Color(0xFF4A3A22) else Color(0xFF262524))
                            .clickable {
                                viewModel.selectSubtitleTrack(track.id)
                                showSubtitleSheet = false
                            }
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = track.label,
                            modifier = Modifier.weight(1f),
                            color = Color.White,
                            fontWeight = if (track.isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
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

@Composable
private fun SeekFeedback(
    visible: Boolean,
    isForward: Boolean
) {
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = if (isForward) Icons.Default.FastForward else Icons.Default.FastRewind, 
                contentDescription = null, 
                tint = Color.White, 
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = if (isForward) "+10s" else "-10s", 
                color = Color.White, 
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}
