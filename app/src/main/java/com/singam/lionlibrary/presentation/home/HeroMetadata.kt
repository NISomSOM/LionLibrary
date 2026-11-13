package com.singam.lionlibrary.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.singam.lionlibrary.R
import com.singam.lionlibrary.domain.model.MediaType
import com.singam.lionlibrary.presentation.components.ImdbBadge
import com.singam.lionlibrary.presentation.components.ImdbYellow
import com.singam.lionlibrary.presentation.components.LetterboxdMark

/** First non-blank genre from the stored comma-separated list. */
internal fun firstHeroGenre(genres: String?): String? =
    genres.orEmpty().split(',').map(String::trim).firstOrNull(String::isNotEmpty)

/** Whole hours and remaining minutes, or null for a missing or non-positive runtime. */
internal fun heroRuntimeParts(minutes: Int?): Pair<Int, Int>? =
    minutes?.takeIf { it > 0 }?.let { it / 60 to it % 60 }

/** Type, first genre, runtime and year in that order, skipping whatever is missing. */
internal fun heroFacts(type: String, genres: String?, runtime: String?, year: Int?): List<String> =
    listOfNotNull(
        type.takeIf(String::isNotBlank),
        firstHeroGenre(genres),
        runtime?.takeIf(String::isNotBlank),
        year?.takeIf { it > 0 }?.toString(),
    )

private const val FACT_SEPARATOR = "  •  "

/**
 * Two bold lines under the hero title: "Movie • Drama • 2h 5m • 2023", then the score, the cached
 * Letterboxd rating and the original language. Lines with nothing to show are left out.
 */
@Composable
internal fun HeroMetadata(
    mediaType: MediaType,
    genres: String?,
    durationMinutes: Int?,
    year: Int?,
    tmdbRating: Float?,
    letterboxdRating: Float?,
    language: String?,
    modifier: Modifier = Modifier,
) {
    val runtime =
        heroRuntimeParts(durationMinutes)?.let { (hours, minutes) ->
            when {
                hours == 0 -> stringResource(R.string.hero_runtime_minutes, minutes)
                minutes == 0 -> stringResource(R.string.hero_runtime_hours, hours)
                else -> stringResource(R.string.hero_runtime_hours_minutes, hours, minutes)
            }
        }
    val type =
        stringResource(
            if (mediaType == MediaType.MOVIE) R.string.hero_type_movie
            else R.string.hero_type_series
        )
    val facts = heroFacts(type, genres, runtime, year)
    val shadow = Shadow(MaterialTheme.colorScheme.scrim.copy(alpha = .7f), Offset(0f, 2f), 8f)
    val tmdb = tmdbRating?.takeIf { it > 0f }
    val letterboxd = letterboxdRating?.takeIf { it > 0f }
    val languageCode = language?.trim()?.uppercase()?.takeIf(String::isNotEmpty)

    Column(
        modifier.fillMaxWidth().padding(top = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (facts.isNotEmpty()) {
            val separatorColor = MaterialTheme.colorScheme.onBackground.copy(alpha = .55f)
            val line = buildAnnotatedString {
                facts.forEachIndexed { index, fact ->
                    if (index > 0)
                        withStyle(SpanStyle(color = separatorColor)) { append(FACT_SEPARATOR) }
                    append(fact)
                }
            }
            Text(
                line,
                Modifier.fillMaxWidth(),
                style =
                    MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        shadow = shadow,
                    ),
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (tmdb != null || letterboxd != null || languageCode != null) {
            val ratingStyle =
                MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    shadow = shadow,
                )
            Row(
                Modifier.padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The user chose to show the TMDB score under an IMDb-style badge.
                if (tmdb != null) {
                    RatingBadge(
                        value = stringResource(R.string.rating_value, tmdb),
                        description = stringResource(R.string.hero_score_description, tmdb),
                        style = ratingStyle.copy(color = ImdbYellow),
                    ) {
                        ImdbBadge()
                    }
                }
                if (letterboxd != null) {
                    RatingBadge(
                        value = stringResource(R.string.rating_value, letterboxd),
                        description =
                            stringResource(R.string.hero_letterboxd_description, letterboxd),
                        style = ratingStyle,
                    ) {
                        LetterboxdMark()
                    }
                }
                if (languageCode != null) {
                    val description =
                        stringResource(R.string.hero_language_description, languageCode)
                    Text(
                        languageCode,
                        Modifier.clearAndSetSemantics { contentDescription = description },
                        style = ratingStyle,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun RatingBadge(
    value: String,
    description: String,
    style: TextStyle,
    mark: @Composable () -> Unit,
) {
    Row(
        Modifier.clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        mark()
        Text(
            value,
            style = style,
            color = style.color.takeOrElse { MaterialTheme.colorScheme.onBackground },
            maxLines = 1,
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Movie, both ratings", widthDp = 360)
@Composable
private fun HeroMetadataMoviePreview() {
    com.singam.lionlibrary.ui.theme.LionLibraryTheme {
        HeroMetadata(MediaType.MOVIE, "Drama, History", 125, 2025, 7.4f, 3.9f, "en")
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Series, TMDB only", widthDp = 360)
@Composable
private fun HeroMetadataSeriesPreview() {
    com.singam.lionlibrary.ui.theme.LionLibraryTheme {
        HeroMetadata(MediaType.TV_SHOW, "Animation, Comedy", 13, 2007, 8.1f, null, "ja")
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "No ratings or runtime", widthDp = 360)
@Composable
private fun HeroMetadataSparsePreview() {
    com.singam.lionlibrary.ui.theme.LionLibraryTheme {
        HeroMetadata(MediaType.ANIME, null, null, 2019, null, null, null)
    }
}

@androidx.compose.ui.tooling.preview.Preview(
    name = "Hero metadata enlarged",
    widthDp = 320,
    fontScale = 2f,
)
@Composable
private fun HeroMetadataEnlargedPreview() {
    com.singam.lionlibrary.ui.theme.LionLibraryTheme {
        HeroMetadata(MediaType.MOVIE, "Science Fiction, Drama", 166, 2024, 8.2f, 4.3f, "en")
    }
}
