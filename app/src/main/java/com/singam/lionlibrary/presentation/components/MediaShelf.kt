package com.singam.lionlibrary.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.singam.lionlibrary.domain.model.MediaItem
import com.singam.lionlibrary.ui.theme.LionLayout
import com.singam.lionlibrary.ui.theme.LionLibraryTheme

/**
 * A titled, horizontally scrolling row of posters, as on Home. With [onHeaderClick] the heading
 * shows an arrow and opens the filtered Library. [shelfKey] identifies the shelf for the
 * poster-to-Details transition; null keeps the plain fade.
 */
@Composable
fun MediaRow(
    title: String,
    items: List<MediaItem>,
    onItemClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    onHeaderClick: (() -> Unit)? = null,
    onItemLongClick: (MediaItem) -> Unit = {},
    shelfKey: String? = null,
    gutter: Dp = LionLayout.Gutter,
) {
    Column(modifier = modifier.padding(bottom = LionLayout.SectionGap)) {
        ShelfHeading(
            title,
            Modifier.padding(horizontal = gutter).padding(bottom = 10.dp),
            onClick = onHeaderClick,
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = gutter),
            horizontalArrangement = Arrangement.spacedBy(LionLayout.ItemGap),
        ) {
            items(items, key = { it.id }) { item ->
                MediaCard(
                    mediaItem = item,
                    onClick = { onItemClick(item) },
                    onLongClick = { onItemLongClick(item) },
                    modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null),
                    sharedShelf = shelfKey,
                )
            }
        }
    }
}

@Preview(name = "Media shelf with arrow")
@Composable
private fun MediaRowWithArrowPreview() {
    LionLibraryTheme {
        MediaRow("Drama", listOf(PreviewFilm, PreviewFilm.copy(id = 2)), {}, onHeaderClick = {})
    }
}

@Preview(name = "Media shelf, large text", widthDp = 320, fontScale = 2f)
@Composable
private fun MediaRowLargeTextPreview() {
    LionLibraryTheme { MediaRow("Recently added", listOf(PreviewFilm), {}) }
}
