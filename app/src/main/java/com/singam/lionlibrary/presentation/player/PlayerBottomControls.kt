package com.singam.lionlibrary.presentation.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.singam.lionlibrary.R
import com.singam.lionlibrary.domain.model.PlaybackChapter
import com.singam.lionlibrary.presentation.player.engine.AspectMode
import com.singam.lionlibrary.ui.theme.LionLibraryTheme
import java.util.Locale

/**
 * Compact Nuvio-style footer: title, a slim seekbar, then one row holding the action pill on the
 * left and the elapsed / total time on the right. Three rows instead of five keep the whole block
 * clear of the centre play button on a landscape phone.
 */
@Composable
internal fun PlayerBottomControls(
    title: String,
    subtitle: String,
    currentPositionMs: () -> Long,
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
    chapters: List<PlaybackChapter> = emptyList(),
) {
    val formatLabels = rememberPlayerFormatLabels()
    Column(modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // Never reaches under the centre controls, however long the title is.
            modifier = Modifier.fillMaxWidth(.6f),
        )
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(.6f),
            )
        }
        PlayerSeekBar(currentPositionMs, durationMs, chapters, onScrub, onScrubEnd)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.weight(1f, fill = false),
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.scrim.copy(alpha = .7f),
                contentColor = MaterialTheme.colorScheme.onBackground,
                border =
                    BorderStroke(.5.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = .25f)),
            ) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
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
                                PlayerFormatting.speed(playbackSpeed, formatLabels),
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

            PlayerTimes(currentPositionMs, durationMs)
        }
    }
}

/** "12:34 / 1:30:00" with tabular digits so the line does not jitter as seconds tick. */
@Composable
private fun PlayerTimes(positionMs: () -> Long, durationMs: Long, modifier: Modifier = Modifier) {
    val elapsed = formatTime(positionMs())
    val total = formatTime(durationMs)
    val style =
        MaterialTheme.typography.labelLarge.copy(
            fontFeatureSettings = "tnum",
            shadow =
                Shadow(
                    MaterialTheme.colorScheme.scrim.copy(alpha = .8f),
                    blurRadius = 6f,
                ),
        )
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(elapsed, style = style, color = MaterialTheme.colorScheme.onBackground, maxLines = 1)
        Text(
            " / ",
            style = style,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = .5f),
            maxLines = 1,
        )
        Text(
            total,
            style = style,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = .75f),
            maxLines = 1,
        )
    }
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
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
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
    LionLibraryTheme {
        PlayerBottomControls(
            title = title,
            subtitle = subtitle,
            currentPositionMs = { positionMs },
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

@Preview(name = "First episode", widthDp = 760)
@Composable
private fun EpisodeControlsPreview() = BottomControlsPreview(isEpisode = true, hasPrevious = false)

@Preview(name = "Last episode", widthDp = 760)
@Composable
private fun LastEpisodeControlsPreview() = BottomControlsPreview(isEpisode = true, hasNext = false)

@Preview(name = "Movie, speed and zoom active", widthDp = 760)
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

@Preview(name = "Unknown duration", widthDp = 760)
@Composable
private fun UnknownDurationControlsPreview() =
    BottomControlsPreview(isEpisode = false, positionMs = 0, durationMs = 0)

@Preview(
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
