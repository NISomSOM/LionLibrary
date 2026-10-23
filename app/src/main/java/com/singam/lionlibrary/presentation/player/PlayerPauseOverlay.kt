package com.singam.lionlibrary.presentation.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.singam.lionlibrary.R
import com.singam.lionlibrary.presentation.components.LocalArtwork

/**
 * "You're watching" card shown while paused with the controls hidden: the title logo (or the title
 * as text), the episode or year line and the synopsis. It has no input handling, so taps reach the
 * gesture layer and bring the controls back.
 */
@Composable
internal fun PlayerPauseOverlay(
    title: String,
    logoPath: String?,
    subtitle: String,
    overview: String?,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier.background(
            Brush.horizontalGradient(
                listOf(
                    MaterialTheme.colorScheme.scrim.copy(alpha = .85f),
                    MaterialTheme.colorScheme.scrim.copy(alpha = .45f),
                    Color.Transparent,
                )
            )
        )
    ) {
        val compact = maxHeight < 420.dp
        Column(
            Modifier.fillMaxSize()
                .safeDrawingPadding()
                .padding(
                    horizontal = 48.dp,
                    vertical = if (compact) 24.dp else 56.dp,
                ),
            verticalArrangement = Arrangement.Bottom,
        ) {
            Text(
                stringResource(R.string.ui_youre_watching),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(if (compact) 8.dp else 12.dp))
            val titleText: @Composable () -> Unit = {
                Text(
                    title,
                    style =
                        if (compact) MaterialTheme.typography.headlineMedium
                        else MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = if (compact) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (logoPath == null) {
                titleText()
            } else {
                // The title stays as the fallback if the stored logo cannot be decoded.
                LocalArtwork(
                    paths = listOf(logoPath),
                    contentDescription = title,
                    modifier = Modifier.fillMaxWidth(.55f).height(if (compact) 64.dp else 96.dp),
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.BottomStart,
                    fallback = titleText,
                )
            }
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (!overview.isNullOrBlank()) {
                Text(
                    overview,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (compact) 2 else 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier =
                        Modifier.padding(top = 12.dp).fillMaxWidth(if (compact) .8f else .6f),
                )
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(
    name = "Paused, episode",
    widthDp = 800,
    heightDp = 380,
)
@Composable
private fun PauseEpisodePreview() {
    com.singam.lionlibrary.ui.theme.LionLibraryTheme {
        PlayerPauseOverlay(
            title = "Frieren: Beyond Journey's End",
            logoPath = null,
            subtitle = "S1 E12 · A Real Hero",
            overview =
                "Frieren and her companions reach a village that once honoured the hero " +
                    "Himmel, and learn what became of the statue built in his memory.",
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Paused, movie", widthDp = 900, heightDp = 480)
@Composable
private fun PauseMoviePreview() {
    com.singam.lionlibrary.ui.theme.LionLibraryTheme {
        PlayerPauseOverlay("Tenzing", null, "2026", null, Modifier.fillMaxSize())
    }
}

@androidx.compose.ui.tooling.preview.Preview(
    name = "Paused, large font",
    widthDp = 800,
    heightDp = 380,
    fontScale = 2f,
)
@Composable
private fun PauseLargeFontPreview() {
    com.singam.lionlibrary.ui.theme.LionLibraryTheme {
        PlayerPauseOverlay(
            "The Expanse",
            null,
            "S2 E5 · Home",
            "A long synopsis that has to be cut off after a few lines on small screens.",
            Modifier.fillMaxSize(),
        )
    }
}
