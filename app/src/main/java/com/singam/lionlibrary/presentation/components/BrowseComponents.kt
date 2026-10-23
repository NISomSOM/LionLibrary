package com.singam.lionlibrary.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.singam.lionlibrary.R
import com.singam.lionlibrary.ui.theme.LionLibraryTheme

@Composable
internal fun PageHeading(title: String, modifier: Modifier = Modifier) {
    Text(
        title,
        modifier.padding(top = 20.dp, bottom = 14.dp),
        style = MaterialTheme.typography.headlineLarge,
        color = MaterialTheme.colorScheme.onBackground,
    )
}

@Composable
internal fun LibrarySearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge,
        placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty())
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Clear, stringResource(R.string.ui_clear))
                }
        },
        shape = RoundedCornerShape(12.dp),
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            ),
    )
}

@Composable
internal fun LibraryFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected,
        onClick,
        label,
        modifier,
        shape = RoundedCornerShape(18.dp),
        colors =
            FilterChipDefaults.filterChipColors(
                containerColor = MaterialTheme.colorScheme.surface,
                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.primary,
            ),
        border =
            BorderStroke(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .45f)
                else MaterialTheme.colorScheme.outline,
            ),
    )
}

@Composable
internal fun ShelfHeading(
    title: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    if (onClick == null) {
        ShelfHeadingContent(title, false, modifier)
    } else {
        TextButton(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            contentPadding = PaddingValues(0.dp),
        ) {
            ShelfHeadingContent(title, true)
        }
    }
}

@Composable
private fun ShelfHeadingContent(title: String, showArrow: Boolean, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (showArrow)
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
    }
}

@Preview(name = "Browsing controls")
@Preview(name = "Browsing controls large text", widthDp = 320, fontScale = 2f)
@Composable
private fun BrowseControlsPreview() {
    LionLibraryTheme {
        Column(Modifier.padding(16.dp)) {
            PageHeading("Library")
            LibrarySearchField("", {}, "Search your library")
            LibrarySearchField("Frieren", {}, "Search your library")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LibraryFilterChip(true, {}, { Text("Movies") })
                LibraryFilterChip(false, {}, { Text("Anime") })
            }
            ShelfHeading("Recently added")
            ShelfHeading("Movies", onClick = {})
        }
    }
}
