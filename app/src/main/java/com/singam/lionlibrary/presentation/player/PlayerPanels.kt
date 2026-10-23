package com.singam.lionlibrary.presentation.player

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.singam.lionlibrary.R
import com.singam.lionlibrary.presentation.player.engine.EngineTrackInfo

private val PanelShape = RoundedCornerShape(20.dp)
private val RowShape = RoundedCornerShape(14.dp)
private const val SUBTITLE_DELAY_STEP_MS = 250L
private const val SUBTITLE_SCALE_STEP = 0.1f

/** Languages, tracks and style for subtitles, in three columns. */
@Composable
internal fun SubtitlePanel(
    tracks: List<EngineTrackInfo>,
    languageFilter: String?,
    delayMs: Long,
    scale: Float,
    supportsDelay: Boolean,
    supportsScale: Boolean,
    onLanguageFilter: (String?) -> Unit,
    onTrackSelect: (String?) -> Unit,
    onDelayChange: (Long) -> Unit,
    onDelayReset: () -> Unit,
    onScaleChange: (Float) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val languages = PlayerFormatting.subtitleLanguages(tracks)
    val visible = PlayerFormatting.filterSubtitles(tracks, languageFilter)
    PanelSurface(modifier) {
        PanelHeader(stringResource(R.string.ui_subtitles), onDismiss)
        Row(
            Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LazyColumn(Modifier.weight(.8f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                item { ColumnLabel(stringResource(R.string.ui_languages)) }
                item {
                    LanguageRow(
                        name = stringResource(R.string.ui_all),
                        count = tracks.size,
                        isSelected = languageFilter == null,
                        onClick = { onLanguageFilter(null) },
                    )
                }
                items(languages, key = { it.language }) { group ->
                    LanguageRow(
                        name =
                            PlayerFormatting.languageName(group.language)
                                ?: stringResource(R.string.ui_unknown_language),
                        count = group.count,
                        isSelected = languageFilter == group.language,
                        onClick = { onLanguageFilter(group.language) },
                    )
                }
            }
            LazyColumn(Modifier.weight(1.3f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                item { ColumnLabel(stringResource(R.string.ui_tracks)) }
                item {
                    TrackRow(
                        title = stringResource(R.string.ui_off),
                        detail = null,
                        chip = null,
                        isSelected = tracks.none { it.isSelected },
                        onClick = { onTrackSelect(null) },
                    )
                }
                items(visible, key = { it.id }) { track ->
                    TrackRow(
                        title = track.label,
                        detail =
                            listOfNotNull(
                                    PlayerFormatting.languageName(track.language),
                                    track.codec,
                                )
                                .distinct()
                                .joinToString(" · ")
                                .ifBlank { null },
                        chip =
                            stringResource(
                                if (track.isExternal) R.string.ui_external else R.string.ui_built_in
                            ),
                        isSelected = track.isSelected,
                        onClick = { onTrackSelect(track.id) },
                    )
                }
            }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ColumnLabel(stringResource(R.string.ui_style))
                StepperRow(
                    label = stringResource(R.string.ui_subtitle_delay),
                    value = PlayerFormatting.subtitleDelay(delayMs),
                    enabled = supportsDelay,
                    onDecrease = { onDelayChange(-SUBTITLE_DELAY_STEP_MS) },
                    onIncrease = { onDelayChange(SUBTITLE_DELAY_STEP_MS) },
                )
                if (supportsDelay) {
                    TextButton(onClick = onDelayReset, enabled = delayMs != 0L) {
                        Text(stringResource(R.string.ui_reset))
                    }
                }
                StepperRow(
                    label = stringResource(R.string.ui_font_size),
                    value = PlayerFormatting.subtitleScale(scale),
                    enabled = supportsScale,
                    onDecrease = { onScaleChange(-SUBTITLE_SCALE_STEP) },
                    onIncrease = { onScaleChange(SUBTITLE_SCALE_STEP) },
                )
                if (!supportsDelay || !supportsScale) {
                    Text(
                        stringResource(R.string.ui_style_engine_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Audio tracks; the selected one is highlighted with its codec and language. */
@Composable
internal fun AudioPanel(
    tracks: List<EngineTrackInfo>,
    onTrackSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PanelSurface(modifier) {
        PanelHeader(stringResource(R.string.ui_audio_tracks), onDismiss)
        if (tracks.isEmpty()) {
            Text(
                stringResource(R.string.ui_no_tracks),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(tracks, key = { it.id }) { track ->
                TrackRow(
                    title = track.label,
                    detail =
                        listOfNotNull(
                                track.codec,
                                PlayerFormatting.channels(track.channels),
                                PlayerFormatting.languageName(track.language),
                            )
                            .distinct()
                            .joinToString(" · ")
                            .ifBlank { null },
                    chip = null,
                    isSelected = track.isSelected,
                    onClick = { onTrackSelect(track.id) },
                )
            }
        }
    }
}

/** Playback speed choices. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SpeedPanel(
    speed: Float,
    onSpeedSelect: (Float) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PanelSurface(modifier) {
        PanelHeader(stringResource(R.string.ui_playback_speed), onDismiss)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PlayerFormatting.speeds.forEach { option ->
                FilterChip(
                    selected = option == speed,
                    onClick = { onSpeedSelect(option) },
                    label = { Text(PlayerFormatting.speed(option)) },
                    colors =
                        FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                )
            }
        }
    }
}

@Composable
private fun PanelSurface(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier,
        shape = PanelShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = .96f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 2.dp,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            content()
        }
    }
}

@Composable
private fun PanelHeader(title: String, onDismiss: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDismiss) {
            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.ui_close))
        }
    }
}

@Composable
private fun ColumnLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 2.dp),
    )
}

@Composable
private fun LanguageRow(name: String, count: Int, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color =
            if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surfaceVariant,
        contentColor =
            if (isSelected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(count.toString(), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun TrackRow(
    title: String,
    detail: String?,
    chip: String?,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RowShape,
        color =
            if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surfaceVariant,
        contentColor =
            if (isSelected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            Modifier.fillMaxWidth()
                .heightIn(min = 52.dp)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (detail != null) {
                    Text(detail, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
            }
            if (chip != null) {
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .12f),
                ) {
                    Text(
                        chip,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            if (isSelected) {
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.ui_selected))
            }
        }
    }
}

@Composable
private fun StepperRow(
    label: String,
    value: String,
    enabled: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    Column {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = onDecrease, enabled = enabled) {
                Icon(
                    Icons.Rounded.Remove,
                    contentDescription = stringResource(R.string.ui_decrease),
                )
            }
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color =
                    if (enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = .38f),
                modifier = Modifier.widthIn(min = 72.dp).padding(horizontal = 8.dp),
                maxLines = 1,
            )
            FilledTonalIconButton(onClick = onIncrease, enabled = enabled) {
                Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.ui_increase))
            }
        }
    }
}

private val previewSubtitles =
    listOf(
        EngineTrackInfo("1", "English", true, language = "en", codec = "SRT"),
        EngineTrackInfo("2", "English SDH", false, language = "en", codec = "ASS"),
        EngineTrackInfo("3", "Japanese", false, language = "ja", codec = "PGS"),
        EngineTrackInfo("4", "Sidecar subtitles", false, codec = "SRT", isExternal = true),
    )

private val previewAudio =
    listOf(
        EngineTrackInfo("a", "Japanese", true, language = "ja", codec = "E-AC3", channels = 6),
        EngineTrackInfo("b", "English", false, language = "en", codec = "AAC", channels = 2),
    )

@androidx.compose.ui.tooling.preview.Preview(name = "Subtitles, VLC", widthDp = 800, heightDp = 360)
@Composable
private fun SubtitlePanelVlcPreview() {
    com.singam.lionlibrary.ui.theme.LionLibraryTheme {
        SubtitlePanel(previewSubtitles, null, 500, 1f, true, false, {}, {}, {}, {}, {}, {})
    }
}

@androidx.compose.ui.tooling.preview.Preview(
    name = "Subtitles, ExoPlayer filtered",
    widthDp = 800,
    heightDp = 360,
)
@Composable
private fun SubtitlePanelExoPreview() {
    com.singam.lionlibrary.ui.theme.LionLibraryTheme {
        SubtitlePanel(previewSubtitles, "en", 0, 1.2f, false, true, {}, {}, {}, {}, {}, {})
    }
}

@androidx.compose.ui.tooling.preview.Preview(
    name = "Subtitles, none",
    widthDp = 800,
    heightDp = 360,
)
@Composable
private fun SubtitlePanelEmptyPreview() {
    com.singam.lionlibrary.ui.theme.LionLibraryTheme {
        SubtitlePanel(emptyList(), null, 0, 1f, true, false, {}, {}, {}, {}, {}, {})
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Audio", widthDp = 360)
@Composable
private fun AudioPanelPreview() {
    com.singam.lionlibrary.ui.theme.LionLibraryTheme { AudioPanel(previewAudio, {}, {}) }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Audio, none", widthDp = 360)
@Composable
private fun AudioPanelEmptyPreview() {
    com.singam.lionlibrary.ui.theme.LionLibraryTheme { AudioPanel(emptyList(), {}, {}) }
}

@androidx.compose.ui.tooling.preview.Preview(
    name = "Audio, large font",
    widthDp = 360,
    fontScale = 2f,
)
@Composable
private fun AudioPanelLargeFontPreview() {
    com.singam.lionlibrary.ui.theme.LionLibraryTheme { AudioPanel(previewAudio, {}, {}) }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Speed", widthDp = 420)
@Composable
private fun SpeedPanelPreview() {
    com.singam.lionlibrary.ui.theme.LionLibraryTheme { SpeedPanel(1.25f, {}, {}) }
}
