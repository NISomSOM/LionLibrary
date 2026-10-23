package com.singam.lionlibrary.presentation.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.singam.lionlibrary.R
import com.singam.lionlibrary.domain.model.*
import com.singam.lionlibrary.presentation.components.LibraryFilterChip
import com.singam.lionlibrary.presentation.components.LibrarySearchField
import com.singam.lionlibrary.presentation.components.MediaCard
import com.singam.lionlibrary.presentation.components.PageHeading
import com.singam.lionlibrary.presentation.navigation.LocalFloatingNavigationInset
import com.singam.lionlibrary.ui.theme.LionLibraryTheme
import org.koin.androidx.compose.koinViewModel

@Composable
fun LibraryRoot(
    initialFilter: MediaFilter? = null,
    onMediaClick: (Long, MediaType) -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: LibraryViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(initialFilter) { initialFilter?.let(viewModel::setInitialFilter) }
    LibraryScreen(state, viewModel::onAction, onMediaClick, onSettingsClick)
}

@Composable
fun LibraryScreen(
    state: LibraryState,
    onAction: (LibraryAction) -> Unit,
    onMediaClick: (Long, MediaType) -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val minimumCardWidth = 104.dp * (LocalDensity.current.fontScale / 1.3f).coerceIn(1f, 1.6f)
    val bottomInset = LocalFloatingNavigationInset.current
    Column(
        modifier
            .fillMaxSize()
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
            )
            .imePadding()
            .padding(horizontal = 16.dp)
    ) {
        PageHeading(
            stringResource(if (state.query.watchlistOnly) R.string.watchlist else R.string.library)
        )
        LibrarySearchField(
            state.query.text,
            { onAction(LibraryAction.QueryChanged(it)) },
            stringResource(R.string.search_library),
            Modifier.padding(bottom = 8.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LibraryFilterChip(
                selected = state.query.watchlistOnly,
                onClick = { onAction(LibraryAction.ToggleWatchlist) },
                label = { Text(stringResource(R.string.watchlist)) },
            )
            LibraryFilterChip(
                selected = state.filtersVisible,
                onClick = { onAction(LibraryAction.ToggleFilters) },
                label = { Text(stringResource(R.string.filter_options)) },
            )
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(MediaFilter.entries) { filter ->
                LibraryFilterChip(
                    selected = state.query.type == filter,
                    onClick = { onAction(LibraryAction.TypeChanged(filter)) },
                    label = { Text(stringResource(filter.label())) },
                )
            }
        }
        if (state.filtersVisible) {
            Column(
                Modifier.weight(1f, fill = false)
                    .heightIn(max = 240.dp)
                    .verticalScroll(androidx.compose.foundation.rememberScrollState())
            ) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(LibrarySort.entries) { sort ->
                        LibraryFilterChip(
                            selected = state.query.sort == sort,
                            onClick = { onAction(LibraryAction.SortChanged(sort)) },
                            label = { Text(stringResource(sort.label())) },
                        )
                    }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(WatchFilter.entries) { watch ->
                        LibraryFilterChip(
                            selected = state.query.watch == watch,
                            onClick = { onAction(LibraryAction.WatchChanged(watch)) },
                            label = { Text(stringResource(watch.label())) },
                        )
                    }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.genres) { genre ->
                        LibraryFilterChip(
                            selected = state.query.genre == genre,
                            onClick = {
                                onAction(
                                    LibraryAction.GenreChanged(
                                        if (state.query.genre == genre) "" else genre
                                    )
                                )
                            },
                            label = { Text(genre) },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = state.query.genre,
                        onValueChange = { onAction(LibraryAction.GenreChanged(it)) },
                        label = { Text(stringResource(R.string.genre)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = state.yearInput,
                        onValueChange = { onAction(LibraryAction.YearChanged(it)) },
                        label = { Text(stringResource(R.string.year)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                TextButton(onClick = { onAction(LibraryAction.Reset) }) {
                    Text(stringResource(R.string.clear_filters))
                }
            }
        }
        state.error?.let {
            Text(it.resolve(LocalContext.current), color = MaterialTheme.colorScheme.error)
        }
        if (state.isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        else if (state.items.isEmpty()) {
            val filtered =
                state.query.text.isNotBlank() ||
                    state.query.genre.isNotBlank() ||
                    state.query.year != null ||
                    state.query.watch != WatchFilter.ALL ||
                    state.query.type != MediaFilter.ALL
            val title =
                if (filtered) R.string.filter_empty
                else if (state.query.watchlistOnly) R.string.watchlist_empty
                else R.string.library_empty
            val help =
                if (filtered) R.string.filter_empty_help
                else if (state.query.watchlistOnly) R.string.watchlist_empty_help
                else R.string.library_empty_help
            Column(
                Modifier.weight(1f)
                    .verticalScroll(androidx.compose.foundation.rememberScrollState())
                    .padding(top = 16.dp, bottom = bottomInset + 16.dp)
            ) {
                Text(stringResource(title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(help), style = MaterialTheme.typography.bodyLarge)
                if (filtered)
                    TextButton(onClick = { onAction(LibraryAction.Reset) }) {
                        Text(stringResource(R.string.clear_filters))
                    }
                else if (!state.query.watchlistOnly)
                    Button(onClick = onSettingsClick) {
                        Text(stringResource(R.string.open_settings))
                    }
            }
        } else
            LazyVerticalGrid(
                modifier = Modifier.weight(1f),
                columns = GridCells.Adaptive(minimumCardWidth),
                contentPadding = PaddingValues(top = 16.dp, bottom = bottomInset + 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.items, key = { it.id }) { item ->
                    MediaCard(
                        item,
                        { onMediaClick(item.id, item.mediaType) },
                        Modifier.fillMaxWidth(),
                        onToggleWatchlist = {
                            onAction(LibraryAction.ToggleItemWatchlist(item.id))
                        },
                    )
                }
            }
    }
}

internal fun MediaFilter.label() =
    when (this) {
        MediaFilter.ALL -> R.string.all
        MediaFilter.MOVIES -> R.string.movies
        MediaFilter.TV_SHOWS -> R.string.tv_shows
        MediaFilter.ANIME -> R.string.anime
    }

internal fun LibrarySort.label() =
    when (this) {
        LibrarySort.TITLE_ASC -> R.string.title_asc
        LibrarySort.TITLE_DESC -> R.string.title_desc
        LibrarySort.RECENT -> R.string.recent
        LibrarySort.YEAR -> R.string.year_newest
        LibrarySort.RATING -> R.string.rating_highest
    }

internal fun WatchFilter.label() =
    when (this) {
        WatchFilter.ALL -> R.string.all
        WatchFilter.UNWATCHED -> R.string.unwatched
        WatchFilter.IN_PROGRESS -> R.string.watch_in_progress
        WatchFilter.WATCHED -> R.string.watched
    }

@Preview(name = "Loading")
@Composable
private fun LoadingPreview() {
    LionLibraryTheme { LibraryScreen(LibraryState(), {}, { _, _ -> }, {}) }
}

@Preview(name = "Empty")
@Composable
private fun EmptyPreview() {
    LionLibraryTheme { LibraryScreen(LibraryState(isLoading = false), {}, { _, _ -> }, {}) }
}

@Preview(name = "Watchlist")
@Composable
private fun WatchlistPreview() {
    LionLibraryTheme {
        LibraryScreen(
            LibraryState(query = LibraryQuery(watchlistOnly = true), isLoading = false),
            {},
            { _, _ -> },
            {},
        )
    }
}

@Preview(name = "Filters", widthDp = 840)
@Preview(name = "Large text", fontScale = 2f)
@Composable
private fun FiltersPreview() {
    LionLibraryTheme {
        LibraryScreen(LibraryState(filtersVisible = true, isLoading = false), {}, { _, _ -> }, {})
    }
}

@Preview(name = "Error")
@Composable
private fun ErrorPreview() {
    LionLibraryTheme {
        LibraryScreen(
            LibraryState(
                isLoading = false,
                error =
                    com.singam.lionlibrary.presentation.common.UiText.Resource(
                        R.string.error_unknown
                    ),
            ),
            {},
            { _, _ -> },
            {},
        )
    }
}
