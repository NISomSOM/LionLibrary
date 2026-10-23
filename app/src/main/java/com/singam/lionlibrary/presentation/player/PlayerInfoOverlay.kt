package com.singam.lionlibrary.presentation.player

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
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
import com.singam.lionlibrary.presentation.player.engine.EngineType
import com.singam.lionlibrary.presentation.player.engine.EngineVideoInfo

/** File, engine and stream details, as small labels over large values. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PlayerInfoOverlay(
    info: PlayerInfoUi,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val unknown = stringResource(R.string.ui_info_unknown)
    Column(
        modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.ui_info),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.ui_close))
            }
        }
        InfoSection(stringResource(R.string.ui_info_file)) {
            InfoValue(stringResource(R.string.ui_info_name), info.fileName, wide = true)
            InfoValue(
                stringResource(R.string.ui_info_engine),
                stringResource(
                    if (info.engineType == EngineType.LIBVLC) R.string.ui_engine_vlc
                    else R.string.ui_engine_exoplayer
                ),
            )
        }
        InfoSection(stringResource(R.string.ui_info_video)) {
            val video = info.video
            InfoValue(stringResource(R.string.ui_info_codec), video?.codec ?: unknown)
            InfoValue(
                stringResource(R.string.ui_info_resolution),
                video?.let { PlayerFormatting.resolution(it.width, it.height) } ?: unknown,
            )
            InfoValue(
                stringResource(R.string.ui_info_frame_rate),
                PlayerFormatting.frameRate(video?.frameRate) ?: unknown,
            )
            InfoValue(
                stringResource(R.string.ui_info_bitrate),
                PlayerFormatting.bitrate(video?.bitrate) ?: unknown,
            )
        }
        InfoSection(stringResource(R.string.ui_info_audio)) {
            val audio = info.audio
            if (audio == null) {
                InfoValue(stringResource(R.string.ui_info_track), stringResource(R.string.ui_off))
            } else {
                InfoValue(stringResource(R.string.ui_info_codec), audio.codec ?: unknown)
                InfoValue(
                    stringResource(R.string.ui_info_channels),
                    PlayerFormatting.channels(audio.channels) ?: unknown,
                )
                InfoValue(
                    stringResource(R.string.ui_info_sample_rate),
                    PlayerFormatting.sampleRate(audio.sampleRateHz) ?: unknown,
                )
                InfoValue(
                    stringResource(R.string.ui_info_language),
                    PlayerFormatting.languageName(audio.language) ?: unknown,
                )
            }
        }
        InfoSection(stringResource(R.string.ui_info_subtitle)) {
            val subtitle = info.subtitle
            if (subtitle == null) {
                InfoValue(stringResource(R.string.ui_info_track), stringResource(R.string.ui_off))
            } else {
                InfoValue(stringResource(R.string.ui_info_name), subtitle.label)
                InfoValue(
                    stringResource(R.string.ui_info_language),
                    PlayerFormatting.languageName(subtitle.language) ?: unknown,
                )
                InfoValue(
                    stringResource(R.string.ui_info_source),
                    stringResource(
                        if (subtitle.isExternal) R.string.ui_external else R.string.ui_built_in
                    ),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InfoSection(title: String, content: @Composable FlowRowScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
private fun InfoValue(label: String, value: String, wide: Boolean = false) {
    Column(Modifier.widthIn(min = 120.dp, max = if (wide) 560.dp else 280.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Info, full", widthDp = 800, heightDp = 420)
@Composable
private fun InfoFullPreview() {
    com.singam.lionlibrary.ui.theme.LionLibraryTheme {
        PlayerInfoOverlay(
            PlayerInfoUi(
                fileName = "Frieren - S01E12 - A Real Hero [1080p].mkv",
                engineType = EngineType.LIBVLC,
                video = EngineVideoInfo("HEVC", 1920, 1080, 23.976f, 4_200_000),
                audio =
                    EngineTrackInfo(
                        "a",
                        "Japanese",
                        true,
                        language = "ja",
                        codec = "E-AC3",
                        channels = 6,
                        sampleRateHz = 48_000,
                    ),
                subtitle =
                    EngineTrackInfo("s", "English", true, language = "en", isExternal = true),
            ),
            {},
            Modifier.padding(24.dp),
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Info, sparse", widthDp = 800, heightDp = 420)
@Composable
private fun InfoSparsePreview() {
    com.singam.lionlibrary.ui.theme.LionLibraryTheme {
        PlayerInfoOverlay(
            PlayerInfoUi("movie.mp4", EngineType.EXOPLAYER, null, null, null),
            {},
            Modifier.padding(24.dp),
        )
    }
}
