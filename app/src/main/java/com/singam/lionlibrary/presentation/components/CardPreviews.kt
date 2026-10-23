package com.singam.lionlibrary.presentation.components

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.singam.lionlibrary.domain.model.*
import com.singam.lionlibrary.ui.theme.LionLibraryTheme

internal val PreviewFilm =
    MediaItem(
        id = 1,
        tmdbId = null,
        title = "Knapsack Kid",
        originalTitle = null,
        overview = null,
        posterPath = null,
        backdropPath = null,
        genres = "Animation,Anime",
        rating = null,
        year = 2023,
        mediaType = MediaType.MOVIE,
        matchConfidence = 0f,
        isUnidentified = true,
        lastUpdated = 1,
    )

@Preview(name = "Local files and long titles")
@Preview(name = "Cards with large text", fontScale = 2f)
@Composable
private fun MediaCardsPreview() {
    LionLibraryTheme {
        Row {
            MediaCard(PreviewFilm, {}, onToggleWatchlist = {})
            MediaCard(
                PreviewFilm.copy(
                    title = "Rascal Does Not Dream of a Knapsack Kid",
                    isUnidentified = false,
                    isWatchlisted = true,
                ),
                {},
                onToggleWatchlist = {},
            )
        }
    }
}

@Preview(name = "Continue watching without artwork")
@Composable
private fun JumpBackInPreview() {
    LionLibraryTheme {
        JumpBackInCard(
            JumpBackInItem(
                1,
                MediaType.ANIME,
                "Frieren: Beyond Journey’s End",
                null,
                null,
                2,
                "A new journey",
                2,
                1,
                null,
                .5f,
                filePath = "content://fixture/episode",
                lastWatched = 1,
            ),
            {},
            onLongClick = {},
        )
    }
}

@Preview(name = "Identified poster without actions")
@Composable
private fun IdentifiedPosterPreview() {
    LionLibraryTheme { MediaCard(PreviewFilm.copy(isUnidentified = false), {}) }
}

@Preview(name = "Unidentified poster, no year")
@Composable
private fun UnidentifiedPosterPreview() {
    LionLibraryTheme { MediaCard(PreviewFilm.copy(year = null), {}) }
}

@Preview(name = "Watchlist available")
@Composable
private fun WatchlistPosterPreview() {
    LionLibraryTheme {
        MediaCard(PreviewFilm.copy(isUnidentified = false), {}, onToggleWatchlist = {})
    }
}

@Preview(name = "Resume movie")
@Composable
private fun ResumeMoviePreview() {
    LionLibraryTheme {
        JumpBackInCard(
            JumpBackInItem(
                1,
                MediaType.MOVIE,
                "Michael",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                .5f,
                filePath = "content://fixture/movie",
                lastWatched = 1,
            ),
            {},
            onLongClick = {},
        )
    }
}

@Preview(name = "Next episode")
@Preview(name = "Next episode enlarged text", fontScale = 2f)
@Composable
private fun NextEpisodePreview() {
    LionLibraryTheme {
        JumpBackInCard(
            JumpBackInItem(
                1,
                MediaType.ANIME,
                "Frieren: Beyond Journey?s End",
                null,
                null,
                2,
                "A new journey",
                2,
                1,
                null,
                null,
                isNextUp = true,
                filePath = "content://fixture/episode",
                lastWatched = 1,
            ),
            {},
            onLongClick = {},
        )
    }
}
