package com.singam.lionlibrary.presentation.home

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Remove whole genres, preserving year/rating and Unicode text rather than slicing characters. */
internal fun fitHeroMetadata(
    year: String?,
    genres: String?,
    rating: String?,
    fits: (String) -> Boolean,
): String {
    val candidates =
        genres.orEmpty().split(',').map(String::trim).filter(String::isNotEmpty).take(2)
    fun line(count: Int) =
        listOfNotNull(
                year?.takeIf(String::isNotBlank),
                candidates.take(count).joinToString(" · ").takeIf(String::isNotBlank),
                rating?.takeIf(String::isNotBlank),
            )
            .joinToString("  ·  ")
    return (candidates.size downTo 0).map(::line).firstOrNull(fits) ?: line(0)
}

@Composable
internal fun HeroMetadataLine(
    year: String?,
    genres: String?,
    rating: String?,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.bodyMedium
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxWidth().padding(top = 12.dp)) {
        val widthPx = with(density) { maxWidth.roundToPx() }
        // Measurements depend on content, available width and font scale, never pager animation
        // frames.
        val metadata =
            remember(year, genres, rating, widthPx, style, density, measurer) {
                fitHeroMetadata(year, genres, rating) {
                    measurer
                        .measure(it, style = style, softWrap = false, maxLines = 1)
                        .size
                        .width <= widthPx
                }
            }
        if (metadata.isNotBlank()) {
            Text(
                metadata,
                Modifier.fillMaxWidth(),
                style = style,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = .85f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Long hero genres", widthDp = 320)
@Composable
private fun HeroMetadataPreview() {
    com.singam.lionlibrary.ui.theme.LionLibraryTheme {
        HeroMetadataLine("2023", "Animation, Science Fiction", "★ 8.1")
    }
}

@androidx.compose.ui.tooling.preview.Preview(
    name = "Hero metadata enlarged",
    widthDp = 320,
    fontScale = 2f,
)
@Composable
private fun EnlargedHeroMetadataPreview() {
    com.singam.lionlibrary.ui.theme.LionLibraryTheme {
        HeroMetadataLine("2023", "Animation, Science Fiction", "★ 8.1")
    }
}
