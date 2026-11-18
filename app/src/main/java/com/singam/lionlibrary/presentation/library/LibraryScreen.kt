package com.singam.lionlibrary.presentation.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.singam.lionlibrary.R
import com.singam.lionlibrary.domain.model.*
import com.singam.lionlibrary.presentation.common.UiText
import com.singam.lionlibrary.presentation.components.DeletionDialog
import com.singam.lionlibrary.presentation.components.LIBRARY_SKELETON_CELLS
import com.singam.lionlibrary.presentation.components.LibraryFilterChip
import com.singam.lionlibrary.presentation.components.LibrarySearchField
import com.singam.lionlibrary.presentation.components.LibrarySkeletonCell
import com.singam.lionlibrary.presentation.components.MediaCard
import com.singam.lionlibrary.presentation.components.MediaItemPreview
import com.singam.lionlibrary.presentation.components.PageHeading
import com.singam.lionlibrary.presentation.components.ShimmerHost
import com.singam.lionlibrary.presentation.navigation.LocalFloatingNavigationInset
import com.singam.lionlibrary.presentation.navigation.LocalPosterTapOrigin
import com.singam.lionlibrary.ui.theme.LionLayout
import com.singam.lionlibrary.ui.theme.LionLibraryTheme
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@Composable
fun LibraryRoot(
    snackbarHostState: SnackbarHostState,
    initialFilter: MediaFilter? = null,
    initialGenres: List<String> = emptyList(),
    onMediaClick: (Long, MediaType) -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: LibraryViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarScope = rememberCoroutineScope()
    LaunchedEffect(initialFilter, initialGenres) {
        viewModel.setInitialFilter(initialFilter, initialGenres)
    }
    LaunchedEffect(viewModel.events) {
        viewModel.events.collect { event ->
            when (event) {
                // In its own coroutine, so a visible message never delays the next event.
                is LibraryEvent.ShowMessage ->
                    snackbarScope.launch {
                        snackbarHostState.showSnackbar(event.message.resolve(context))
                    }
            }
        }
    }
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
    val minimumCardWidth =
        LionLayout.libraryCellMin * (LocalDensity.current.fontScale / 1.3f).coerceIn(1f, 1.6f)
    val bottomInset = LocalFloatingNavigationInset.current
    DeletionDialog(
        state.deletion,
        { onAction(LibraryAction.ConfirmDelete) },
        { onAction(LibraryAction.DismissDelete) },
    )
    // Poster long press: the poster lifts out of the grid over a blurred/dimmed page.
    state.selectedMediaForOptions?.let { media ->
        MediaItemPreview(
            media = media,
            onOpen = {
                onAction(LibraryAction.MediaOptions(null))
                onMediaClick(media.id, media.mediaType)
            },
            onDismiss = { onAction(LibraryAction.MediaOptions(null)) },
            onMarkWatched = { onAction(LibraryAction.MarkWatched(media)) },
            onToggleWatchlist = { onAction(LibraryAction.ToggleItemWatchlist(media.id)) },
            onDelete = { onAction(LibraryAction.RequestDelete(media.id)) },
        )
    }
    val tapOrigin = LocalPosterTapOrigin.current
    val showSkeleton = state.isLoading && state.items.isEmpty()
    val skeletonDescription = stringResource(R.string.loading_content)
    // One grid scrolls the whole page: heading, search and filters move up with the posters.
    ShimmerHost(active = showSkeleton) {
        LazyVerticalGrid(
            // The grid's own bounds are where its cards can show: a poster flying to or from a
            // card half under the status bar is cut off there too.
            modifier =
                modifier
                    .fillMaxSize()
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                        )
                    )
                    .imePadding()
                    .onGloballyPositioned {
                        tapOrigin?.onViewport(LIBRARY_SHELF, it.boundsInRoot())
                    },
            columns = GridCells.Adaptive(minimumCardWidth),
            contentPadding =
                PaddingValues(start = 16.dp, end = 16.dp, bottom = bottomInset + 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "header", span = { GridItemSpan(maxLineSpan) }, contentType = "header") {
                LibraryHeader(
                    query = state.query,
                    yearInput = state.yearInput,
                    filtersVisible = state.filtersVisible,
                    genres = state.genres,
                    error = state.error,
                    isRefreshing = state.isLoading && state.items.isNotEmpty(),
                    onAction = onAction,
                )
            }
            when {
                // Placeholders only when there is nothing to show yet; a new query over existing
                // results keeps them visible under a thin progress line.
                showSkeleton ->
                    items(LIBRARY_SKELETON_CELLS, contentType = { "skeleton" }) { index ->
                        LibrarySkeletonCell(
                            if (index == 0)
                                Modifier.testTag("library-skeleton").semantics {
                                    contentDescription = skeletonDescription
                                }
                            else Modifier
                        )
                    }
                state.items.isEmpty() ->
                    item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                        LibraryEmptyState(
                            query = state.query,
                            onClearFilters = { onAction(LibraryAction.Reset) },
                            onSettingsClick = onSettingsClick,
                        )
                    }
                else ->
                    items(state.items, key = { it.id }, contentType = { "poster" }) { item ->
                        MediaCard(
                            item,
                            { onMediaClick(item.id, item.mediaType) },
                            Modifier.fillMaxWidth()
                                .animateItem(fadeInSpec = null, fadeOutSpec = null),
                            onToggleWatchlist = {
                                onAction(LibraryAction.ToggleItemWatchlist(item.id))
                            },
                            onLongClick = { onAction(LibraryAction.MediaOptions(item)) },
                            sharedShelf = LIBRARY_SHELF,
                        )
                    }
            }
        }
    }
}

/** Heading, search, the chip rows and (when open) the filter and sort panel. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LibraryHeader(
    query: LibraryQuery,
    yearInput: String,
    filtersVisible: Boolean,
    genres: List<String>,
    error: UiText?,
    isRefreshing: Boolean,
    onAction: (LibraryAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        PageHeading(
            stringResource(if (query.watchlistOnly) R.string.watchlist else R.string.library)
        )
        LibrarySearchField(
            query.text,
            { onAction(LibraryAction.QueryChanged(it)) },
            stringResource(R.string.search_library),
            Modifier.padding(bottom = 8.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LibraryFilterChip(
                selected = query.watchlistOnly,
                onClick = { onAction(LibraryAction.ToggleWatchlist) },
                label = { Text(stringResource(R.string.watchlist)) },
            )
            LibraryFilterChip(
                selected = filtersVisible,
                onClick = { onAction(LibraryAction.ToggleFilters) },
                label = { Text(stringResource(R.string.filter_options)) },
            )
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(MediaFilter.entries) { filter ->
                LibraryFilterChip(
                    selected = query.type == filter,
                    onClick = { onAction(LibraryAction.TypeChanged(filter)) },
                    label = { Text(stringResource(filter.label())) },
                )
            }
        }
        if (filtersVisible) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(LibrarySort.entries) { sort ->
                    LibraryFilterChip(
                        selected = query.sort == sort,
                        onClick = { onAction(LibraryAction.SortChanged(sort)) },
                        label = { Text(stringResource(sort.label())) },
                    )
                }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(WatchFilter.entries) { watch ->
                    LibraryFilterChip(
                        selected = query.watch == watch,
                        onClick = { onAction(LibraryAction.WatchChanged(watch)) },
                        label = { Text(stringResource(watch.label())) },
                    )
                }
            }
            // Several genres can be selected; a title matching any of them is shown.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                genres.forEach { genre ->
                    LibraryFilterChip(
                        selected = query.genres.any { it.equals(genre, ignoreCase = true) },
                        onClick = { onAction(LibraryAction.GenreToggled(genre)) },
                        label = { Text(genre) },
                    )
                }
            }
            OutlinedTextField(
                value = yearInput,
                onValueChange = { onAction(LibraryAction.YearChanged(it)) },
                label = { Text(stringResource(R.string.year)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(.5f),
            )
            TextButton(onClick = { onAction(LibraryAction.Reset) }) {
                Text(stringResource(R.string.clear_filters))
            }
        }
        error?.let {
            Text(it.resolve(LocalContext.current), color = MaterialTheme.colorScheme.error)
        }
        if (isRefreshing) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
}

/** No results: missing setup, an empty watchlist or filters that match nothing. */
@Composable
private fun LibraryEmptyState(
    query: LibraryQuery,
    onClearFilters: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val filtered =
        query.text.isNotBlank() ||
            query.genres.isNotEmpty() ||
            query.year != null ||
            query.watch != WatchFilter.ALL ||
            query.type != MediaFilter.ALL
    val title =
        if (filtered) R.string.filter_empty
        else if (query.watchlistOnly) R.string.watchlist_empty else R.string.library_empty
    val help =
        if (filtered) R.string.filter_empty_help
        else if (query.watchlistOnly) R.string.watchlist_empty_help else R.string.library_empty_help
    Column(modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(help), style = MaterialTheme.typography.bodyLarge)
        if (filtered)
            TextButton(onClick = onClearFilters) { Text(stringResource(R.string.clear_filters)) }
        else if (!query.watchlistOnly)
            Button(onClick = onSettingsClick) { Text(stringResource(R.string.open_settings)) }
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

@Preview(name = "Several genres selected")
@Composable
private fun SelectedGenresPreview() {
    LionLibraryTheme {
        LibraryScreen(
            LibraryState(
                query = LibraryQuery(genres = setOf("Action", "Drama")),
                filtersVisible = true,
                genres = listOf("Action", "Animation", "Anime", "Comedy", "Drama", "Fantasy"),
                isLoading = false,
            ),
            {},
            { _, _ -> },
            {},
        )
    }
}

@Preview(name = "Error")
@Composable
private fun ErrorPreview() {
    LionLibraryTheme {
        LibraryScreen(
            LibraryState(
                isLoading = false,
                error = UiText.Resource(R.string.error_unknown),
            ),
            {},
            { _, _ -> },
            {},
        )
    }
}

/** Shelf name of the Library grid for the poster transition. */
private const val LIBRARY_SHELF = "library"
