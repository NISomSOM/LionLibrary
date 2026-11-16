package com.singam.lionlibrary.presentation.player

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.singam.lionlibrary.R
import com.singam.lionlibrary.domain.model.PlaybackChapter
import com.singam.lionlibrary.ui.theme.LionLibraryTheme

/** Chapter containing [positionMs]: the last one starting at or before it; null without a title. */
internal fun chapterAt(chapters: List<PlaybackChapter>, positionMs: Long): PlaybackChapter? =
    chapters.filter { it.startMs <= positionMs }.maxByOrNull { it.startMs }

/** Chapter starts drawn as marks: inside the file, after its first frame, each once. */
internal fun chapterMarkFractions(chapters: List<PlaybackChapter>, durationMs: Long): List<Float> =
    if (durationMs <= 0) emptyList()
    else
        chapters
            .map { it.startMs }
            .filter { it in 1 until durationMs }
            .distinct()
            .sorted()
            .map { it.toFloat() / durationMs }

/**
 * Thumbless seek bar: a 6 dp track that grows to 10 dp while touched, inside a 48 dp touch target.
 * Chapter starts are notched into the track and the current chapter's name sits above it. The
 * position is read in the draw phase, so playback ticks redraw the bar without recomposing it.
 * Scrubbing reports [onScrub] while the finger moves and [onScrubEnd] once on release.
 */
@Composable
internal fun PlayerSeekBar(
    positionMs: () -> Long,
    durationMs: Long,
    chapters: List<PlaybackChapter>,
    onScrub: (Long) -> Unit,
    onScrubEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Compose-internal: pressed state only drives the track thickness.
    var pressed by remember { mutableStateOf(false) }
    val thickness by animateDpAsState(if (pressed) 10.dp else 6.dp, label = "seekThickness")
    val currentDuration by rememberUpdatedState(durationMs)
    val inactive = MaterialTheme.colorScheme.onBackground.copy(alpha = .25f)
    val active = MaterialTheme.colorScheme.primary
    val notch = MaterialTheme.colorScheme.scrim
    val marks = remember(chapters, durationMs) { chapterMarkFractions(chapters, durationMs) }
    val chapterTitle by
        remember(chapters) {
            derivedStateOf { chapterAt(chapters, positionMs())?.title?.takeIf { it.isNotBlank() } }
        }
    // Whole percent only, so semantics change at most a hundred times per file.
    val percent by
        remember(durationMs) {
            derivedStateOf {
                if (durationMs > 0) (positionMs() * 100 / durationMs).toInt().coerceIn(0, 100)
                else 0
            }
        }
    val description = stringResource(R.string.cd_seek_bar)
    val haptics = LocalHapticFeedback.current
    val currentMarks by rememberUpdatedState(marks)

    Column(modifier.fillMaxWidth()) {
        chapterTitle?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = .8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Box(
            Modifier.fillMaxWidth()
                .height(48.dp)
                .semantics {
                    contentDescription = description
                    progressBarRangeInfo = ProgressBarRangeInfo(percent / 100f, 0f..1f)
                    setProgress { value ->
                        val duration = currentDuration
                        if (duration <= 0) return@setProgress false
                        onScrub((value.coerceIn(0f, 1f) * duration).toLong())
                        onScrubEnd()
                        true
                    }
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val width = size.width.toFloat()
                        // Chapter region under the finger; a change while scrubbing ticks once.
                        var region = -1
                        fun report(x: Float) {
                            val duration = currentDuration
                            if (duration <= 0 || width <= 0f) return
                            val fraction = (x / width).coerceIn(0f, 1f)
                            val now = currentMarks.count { it <= fraction }
                            if (region >= 0 && now != region)
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            region = now
                            onScrub((fraction * duration).toLong())
                        }
                        pressed = true
                        down.consume()
                        report(down.position.x)
                        try {
                            while (true) {
                                val change =
                                    awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                                        ?: break
                                if (!change.pressed) break
                                change.consume()
                                report(change.position.x)
                            }
                        } finally {
                            pressed = false
                            onScrubEnd()
                        }
                    }
                }
                .drawBehind {
                    val height = thickness.toPx()
                    val top = center.y - height / 2
                    val radius = CornerRadius(height / 2)
                    drawRoundRect(inactive, Offset(0f, top), Size(size.width, height), radius)
                    val duration = currentDuration
                    val fraction =
                        if (duration > 0) (positionMs().toFloat() / duration).coerceIn(0f, 1f)
                        else 0f
                    if (fraction > 0f)
                        drawRoundRect(
                            active,
                            Offset(0f, top),
                            Size(size.width * fraction, height),
                            radius,
                        )
                    val gap = 2.dp.toPx()
                    marks.forEach { mark ->
                        drawRect(
                            notch,
                            Offset(size.width * mark - gap / 2, top),
                            Size(gap, height),
                        )
                    }
                }
        )
    }
}

@Composable
private fun SeekBarPreviewFrame(content: @Composable () -> Unit) {
    LionLibraryTheme {
        Box(Modifier.background(MaterialTheme.colorScheme.scrim).padding(16.dp)) { content() }
    }
}

private val PreviewChapters =
    listOf(
        PlaybackChapter(0, 90_000, "Opening"),
        PlaybackChapter(90_000, 1_200_000, "Part A"),
        PlaybackChapter(1_200_000, null, "Ending"),
    )

@Preview(name = "Seek bar with chapters", widthDp = 640)
@Composable
private fun SeekBarChaptersPreview() {
    SeekBarPreviewFrame { PlayerSeekBar({ 300_000 }, 1_400_000, PreviewChapters, {}, {}) }
}

@Preview(name = "Seek bar without chapters", widthDp = 640)
@Composable
private fun SeekBarPlainPreview() {
    SeekBarPreviewFrame { PlayerSeekBar({ 754_000 }, 2_700_000, emptyList(), {}, {}) }
}

@Preview(name = "Seek bar, unknown duration", widthDp = 320)
@Composable
private fun SeekBarUnknownPreview() {
    SeekBarPreviewFrame { PlayerSeekBar({ 0 }, 0, emptyList(), {}, {}) }
}

@Preview(name = "Seek bar, large font", widthDp = 640, fontScale = 2f)
@Composable
private fun SeekBarLargeFontPreview() {
    SeekBarPreviewFrame { PlayerSeekBar({ 300_000 }, 1_400_000, PreviewChapters, {}, {}) }
}
