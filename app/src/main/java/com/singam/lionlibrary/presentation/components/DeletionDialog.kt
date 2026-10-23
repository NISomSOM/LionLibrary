package com.singam.lionlibrary.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.singam.lionlibrary.R
import com.singam.lionlibrary.presentation.common.DeletionUiState

@Composable
fun DeletionDialog(
    state: DeletionUiState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.preparing) {
        AlertDialog(
            onDismissRequest = onDismiss,
            modifier = modifier,
            title = { Text(stringResource(R.string.delete_preparing)) },
            text = { LinearProgressIndicator(Modifier.fillMaxWidth()) },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.delete_cancel)) }
            },
        )
        return
    }
    val preview = state.preview ?: return
    AlertDialog(
        onDismissRequest = { if (!state.deleting) onDismiss() },
        modifier = modifier,
        title = {
            Text(
                stringResource(
                    if (preview.isFolder) R.string.delete_folder_confirm
                    else R.string.delete_file_confirm
                )
            )
        },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(preview.title, style = MaterialTheme.typography.titleMedium)
                Text(preview.location, style = MaterialTheme.typography.bodyMedium)
                Text(
                    if (preview.isFolder)
                        stringResource(R.string.delete_folder_warning, preview.fileCount)
                    else stringResource(R.string.delete_file_warning)
                )
                if (preview.combinedEpisode) Text(stringResource(R.string.delete_combined_warning))
                if (state.deleting) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !state.deleting,
                colors =
                    ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(
                    stringResource(
                        if (state.deleting) R.string.delete_in_progress
                        else if (preview.isFolder) R.string.delete_folder_button
                        else R.string.delete_file_button
                    )
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.deleting) {
                Text(stringResource(R.string.delete_cancel))
            }
        },
    )
}
