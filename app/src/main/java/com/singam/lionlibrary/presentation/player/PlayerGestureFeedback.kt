package com.singam.lionlibrary.presentation.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.singam.lionlibrary.R
import com.singam.lionlibrary.ui.theme.LionLibraryTheme
import kotlin.math.abs
import kotlin.math.roundToInt

/** A horizontal seek drag in progress: signed distance from where it started and the target. */
@Immutable internal data class SeekDragFeedback(val offsetMs: Long, val targetMs: Long)

/** What the last double tap did, shown briefly as text. */
internal enum class DoubleTapFeedback {
    SEEK_BACK,
    SEEK_FORWARD,
    PAUSED,
    RESUMED,
}

/** "+1:05" / "−0:30" for a seek offset. */
internal fun formatSeekOffset(offsetMs: Long): String =
    (if (offsetMs < 0) "−" else "+") + formatTime(abs(offsetMs))

@Composable
private fun feedbackTextStyle(): TextStyle =
    MaterialTheme.typography.titleMedium.copy(
        fontWeight = FontWeight.SemiBold,
        fontFeatureSettings = "tnum",
        shadow = Shadow(MaterialTheme.colorScheme.scrim, blurRadius = 12f),
    )

/**
 * Shadowed text near the top of the video: drag-seek offset and target, the ±10 s or Paused/Playing
 * of a double tap, or the 2x hold indicator. Drag-seek wins when several are active.
 */
@Composable
internal fun PlayerGestureFeedback(
    seekDrag: SeekDragFeedback?,
    doubleTap: DoubleTapFeedback?,
    isHoldSpeedActive: Boolean,
    modifier: Modifier = Modifier,
) {
    val text =
        when {
            seekDrag != null ->
                stringResource(
                    R.string.player_seek_drag,
                    formatSeekOffset(seekDrag.offsetMs),
                    formatTime(seekDrag.targetMs),
                )
            doubleTap == DoubleTapFeedback.SEEK_BACK -> stringResource(R.string.player_seek_back_10)
            doubleTap == DoubleTapFeedback.SEEK_FORWARD ->
                stringResource(R.string.player_seek_forward_10)
            doubleTap == DoubleTapFeedback.PAUSED -> stringResource(R.string.player_paused)
            doubleTap == DoubleTapFeedback.RESUMED -> stringResource(R.string.player_resumed)
            isHoldSpeedActive -> stringResource(R.string.player_hold_speed)
            else -> null
        }
    // Compose-internal: keeps the last text while fading out.
    var shown by remember { mutableStateOf("") }
    if (text != null) shown = text
    val holdDescription = stringResource(R.string.cd_hold_speed)
    AnimatedVisibility(
        visible = text != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        Text(
            shown,
            style = feedbackTextStyle(),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            modifier =
                Modifier.semantics {
                    if (isHoldSpeedActive && seekDrag == null) contentDescription = holdDescription
                },
        )
    }
}

/**
 * Slim vertical level bar shown at a screen edge during a brightness or volume drag: the percentage
 * above, a 4 dp track, the icon below. Fades out shortly after release (the caller clears [level]).
 */
@Composable
internal fun PlayerLevelBar(
    level: Float?,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    // Compose-internal: keeps the last fill while the bar fades out.
    var lastLevel by remember { mutableFloatStateOf(0f) }
    if (level != null) lastLevel = level.coerceIn(0f, 1f)
    AnimatedVisibility(
        visible = level != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        PlayerLevelBarContent(lastLevel, icon, contentDescription)
    }
}

@Composable
private fun PlayerLevelBarContent(
    level: Float,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(R.string.ui_level_percent, (level * 100).roundToInt()),
            style =
                feedbackTextStyle().copy(fontSize = MaterialTheme.typography.labelLarge.fontSize),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
        )
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier.width(4.dp)
                .heightIn(min = 96.dp, max = 160.dp)
                .fillMaxHeight(.4f)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.onBackground.copy(alpha = .3f)),
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
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Preview(name = "Seek drag feedback", widthDp = 640)
@Composable
private fun SeekDragFeedbackPreview() {
    LionLibraryTheme { PlayerGestureFeedback(SeekDragFeedback(65_000, 754_000), null, false) }
}

@Preview(name = "Double tap back")
@Composable
private fun DoubleTapFeedbackPreview() {
    LionLibraryTheme { PlayerGestureFeedback(null, DoubleTapFeedback.SEEK_BACK, false) }
}

@Preview(name = "Double tap forward")
@Composable
private fun DoubleTapForwardFeedbackPreview() {
    LionLibraryTheme { PlayerGestureFeedback(null, DoubleTapFeedback.SEEK_FORWARD, false) }
}

@Preview(name = "Double tap pause")
@Composable
private fun DoubleTapPauseFeedbackPreview() {
    LionLibraryTheme { PlayerGestureFeedback(null, DoubleTapFeedback.PAUSED, false) }
}

@Preview(name = "Double tap resume", fontScale = 2f)
@Composable
private fun DoubleTapResumeFeedbackPreview() {
    LionLibraryTheme { PlayerGestureFeedback(null, DoubleTapFeedback.RESUMED, false) }
}

@Preview(name = "Hold 2x", fontScale = 2f)
@Composable
private fun HoldFeedbackPreview() {
    LionLibraryTheme { PlayerGestureFeedback(null, null, true) }
}

@Preview(name = "Brightness bar", heightDp = 360)
@Composable
private fun BrightnessBarPreview() {
    LionLibraryTheme {
        Box(Modifier.background(MaterialTheme.colorScheme.scrim).padding(16.dp)) {
            PlayerLevelBarContent(.6f, Icons.Default.BrightnessMedium, "Brightness")
        }
    }
}

@Preview(name = "Volume bar full, large font", heightDp = 360, fontScale = 2f)
@Composable
private fun VolumeBarPreview() {
    LionLibraryTheme {
        Box(Modifier.background(MaterialTheme.colorScheme.scrim).padding(16.dp)) {
            PlayerLevelBarContent(1f, Icons.AutoMirrored.Filled.VolumeUp, "Volume")
        }
    }
}
