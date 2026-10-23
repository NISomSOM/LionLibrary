package com.singam.lionlibrary.presentation.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.singam.lionlibrary.R
import com.singam.lionlibrary.presentation.player.engine.AspectMode

/** Title, timeline with time pills, and the bar of player actions along the bottom edge. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PlayerBottomControls(
    title: String,
    subtitle: String,
    currentPositionMs: Long,
    durationMs: Long,
    isEpisode: Boolean,
    hasPrevious: Boolean,
    hasNext: Boolean,
    playbackSpeed: Float,
    aspectMode: AspectMode,
    onScrub: (Long) -> Unit,
    onScrubEnd: () -> Unit,
    onPreviousClick: () -> Unit,
    onSeekForward90Click: () -> Unit,
    onSubtitleClick: () -> Unit,
    onAudioClick: () -> Unit,
    onSpeedClick: () -> Unit,
    onAspectClick: () -> Unit,
    onInfoClick: () -> Unit,
    onNextClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        PlayerSeekBar(currentPositionMs, durationMs, onScrub, onScrubEnd)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            PlayerTimePill(formatTime(currentPositionMs))
            PlayerTimePill(formatTime(durationMs))
        }
        Spacer(Modifier.height(8.dp))
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.scrim.copy(alpha = .7f),
            contentColor = MaterialTheme.colorScheme.onBackground,
            border = BorderStroke(.5.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = .25f)),
        ) {
            FlowRow(
                Modifier.padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.Center,
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                if (isEpisode) {
                    PlayerActionIcon(
                        icon = Icons.Outlined.SkipPrevious,
                        contentDescription = stringResource(R.string.previous_episode),
                        onClick = onPreviousClick,
                        enabled = hasPrevious,
                    )
                }
                TextButton(
                    onClick = onSeekForward90Click,
                    colors =
                        ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onBackground
                        ),
                ) {
                    Text(
                        stringResource(R.string.ui_90s),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                PlayerActionIcon(
                    icon = Icons.Outlined.ClosedCaption,
                    contentDescription = stringResource(R.string.ui_subtitles),
                    onClick = onSubtitleClick,
                )
                PlayerActionIcon(
                    icon = Icons.Outlined.GraphicEq,
                    contentDescription = stringResource(R.string.ui_audio_tracks),
                    onClick = onAudioClick,
                )
                PlayerActionIcon(
                    icon = Icons.Outlined.Speed,
                    contentDescription =
                        stringResource(
                            R.string.ui_speed_value,
                            PlayerFormatting.speed(playbackSpeed),
                        ),
                    onClick = onSpeedClick,
                    isActive = playbackSpeed != 1f,
                )
                PlayerActionIcon(
                    icon = aspectMode.icon(),
                    contentDescription =
                        stringResource(
                            R.string.ui_aspect_value,
                            stringResource(aspectMode.label()),
                        ),
                    onClick = onAspectClick,
                    isActive = aspectMode != AspectMode.FIT,
                )
                PlayerActionIcon(
                    icon = Icons.Outlined.Info,
                    contentDescription = stringResource(R.string.ui_info),
                    onClick = onInfoClick,
                )
                if (isEpisode) {
                    PlayerActionIcon(
                        icon = Icons.Outlined.SkipNext,
                        contentDescription = stringResource(R.string.next_episode),
                        onClick = onNextClick,
                        enabled = hasNext,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerTimePill(time: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.scrim.copy(alpha = .6f),
        contentColor = MaterialTheme.colorScheme.onBackground,
        border = BorderStroke(.5.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = .15f)),
    ) {
        Text(
            time,
            Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerSeekBar(
    currentPositionMs: Long,
    durationMs: Long,
    onScrub: (Long) -> Unit,
    onScrubEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors =
        SliderDefaults.colors(
            thumbColor = MaterialTheme.colorScheme.primary,
            activeTrackColor = MaterialTheme.colorScheme.primary,
            inactiveTrackColor = MaterialTheme.colorScheme.onBackground.copy(alpha = .25f),
        )
    Slider(
        value =
            if (durationMs > 0) (currentPositionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f,
        onValueChange = { onScrub((it * durationMs).toLong()) },
        onValueChangeFinished = onScrubEnd,
        modifier = modifier.fillMaxWidth(),
        colors = colors,
        thumb = {
            Box(Modifier.size(14.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
        },
        track = { sliderState ->
            SliderDefaults.Track(
                sliderState = sliderState,
                modifier = Modifier.height(3.dp),
                colors = colors,
                drawStopIndicator = null,
                thumbTrackGapSize = 0.dp,
            )
        },
    )
}

@Composable
private fun PlayerActionIcon(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isActive: Boolean = false,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors =
            IconButtonDefaults.iconButtonColors(
                contentColor =
                    if (isActive) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onBackground,
                disabledContentColor = MaterialTheme.colorScheme.onBackground.copy(alpha = .35f),
            ),
    ) {
        Icon(icon, contentDescription = contentDescription)
    }
}

private fun AspectMode.icon(): ImageVector =
    when (this) {
        AspectMode.FIT -> Icons.Outlined.AspectRatio
        AspectMode.FILL -> Icons.Outlined.Fullscreen
        AspectMode.ZOOM -> Icons.Outlined.Crop
    }

internal fun AspectMode.label(): Int =
    when (this) {
        AspectMode.FIT -> R.string.ui_aspect_fit
        AspectMode.FILL -> R.string.ui_aspect_fill
        AspectMode.ZOOM -> R.string.ui_aspect_zoom
    }

internal fun formatTime(ms: Long): String {
    if (ms < 0) return "00:00"
    val totalSeconds = ms / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        String.format(java.util.Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(java.util.Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}

@Composable
private fun BottomControlsPreview(
    isEpisode: Boolean,
    hasPrevious: Boolean = true,
    hasNext: Boolean = true,
    title: String = "The Expanse",
    subtitle: String = "S2 E5 · Home",
    positionMs: Long = 754_000,
    durationMs: Long = 2_700_000,
    speed: Float = 1f,
    aspectMode: AspectMode = AspectMode.FIT,
) {
    com.singam.lionlibrary.ui.theme.LionLibraryTheme {
        PlayerBottomControls(
            title = title,
            subtitle = subtitle,
            currentPositionMs = positionMs,
            durationMs = durationMs,
            isEpisode = isEpisode,
            hasPrevious = hasPrevious,
            hasNext = hasNext,
            playbackSpeed = speed,
            aspectMode = aspectMode,
            onScrub = {},
            onScrubEnd = {},
            onPreviousClick = {},
            onSeekForward90Click = {},
            onSubtitleClick = {},
            onAudioClick = {},
            onSpeedClick = {},
            onAspectClick = {},
            onInfoClick = {},
            onNextClick = {},
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "First episode", widthDp = 760)
@Composable
private fun EpisodeControlsPreview() = BottomControlsPreview(isEpisode = true, hasPrevious = false)

@androidx.compose.ui.tooling.preview.Preview(name = "Last episode", widthDp = 760)
@Composable
private fun LastEpisodeControlsPreview() = BottomControlsPreview(isEpisode = true, hasNext = false)

@androidx.compose.ui.tooling.preview.Preview(name = "Movie, speed and zoom active", widthDp = 760)
@Composable
private fun MovieControlsPreview() =
    BottomControlsPreview(
        isEpisode = false,
        title = "Dune: Part Two",
        subtitle = "2024",
        positionMs = 3_700_000,
        durationMs = 9_960_000,
        speed = 1.5f,
        aspectMode = AspectMode.ZOOM,
    )

@androidx.compose.ui.tooling.preview.Preview(name = "Unknown duration", widthDp = 760)
@Composable
private fun UnknownDurationControlsPreview() =
    BottomControlsPreview(isEpisode = false, positionMs = 0, durationMs = 0)

@androidx.compose.ui.tooling.preview.Preview(
    name = "Long title, large font",
    widthDp = 640,
    fontScale = 2f,
)
@Composable
private fun LargeFontControlsPreview() =
    BottomControlsPreview(
        isEpisode = true,
        title = "Frieren: Beyond Journey's End",
        subtitle = "S1 E12 · A Real Hero",
    )
