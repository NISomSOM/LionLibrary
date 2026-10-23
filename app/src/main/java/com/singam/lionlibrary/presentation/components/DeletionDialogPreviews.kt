package com.singam.lionlibrary.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.singam.lionlibrary.domain.repository.DeletionPreview
import com.singam.lionlibrary.presentation.common.DeletionUiState
import com.singam.lionlibrary.ui.theme.LionLibraryTheme

@Preview(name = "Delete file", fontScale = 2f)
@Composable
private fun FileDeletionPreview() {
    LionLibraryTheme {
        DeletionDialog(
            DeletionUiState(DeletionPreview("preview", "Movie", "Movies/Movie.mkv", false, 1)),
            {},
            {},
        )
    }
}

@Preview(name = "Delete show folder")
@Composable
private fun ShowDeletionPreview() {
    LionLibraryTheme {
        DeletionDialog(
            DeletionUiState(DeletionPreview("preview", "Show", "Shows/Show", true, 12)),
            {},
            {},
        )
    }
}

@Preview(name = "Deleting")
@Composable
private fun DeletingPreview() {
    LionLibraryTheme {
        DeletionDialog(
            DeletionUiState(
                DeletionPreview("preview", "Movie", "Movies/Movie.mkv", false, 1),
                deleting = true,
            ),
            {},
            {},
        )
    }
}

@Preview(name = "Checking storage")
@Composable
private fun CheckingDeletionPreview() {
    LionLibraryTheme { DeletionDialog(DeletionUiState(preparing = true), {}, {}) }
}
