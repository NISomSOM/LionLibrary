package com.example.mediahub.presentation.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mediahub.ui.theme.DarkSurfaceVariant
import com.example.mediahub.ui.theme.OrangeAccent
import com.example.mediahub.ui.theme.SuccessGreen
import com.example.mediahub.ui.theme.TextTertiary
import com.example.mediahub.util.FolderPickerHelper
import org.koin.androidx.compose.koinViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


@Composable
fun SettingsRoot(
    snackbarHostState: SnackbarHostState,
    viewModel: SettingsViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SettingsEvent.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(event.message)
                }
            }
        }
    }

    SettingsScreen(
        state = state,
        onAction = viewModel::onAction
    )
}


@Composable
fun SettingsScreen(
    state: SettingsState,
    onAction: (SettingsAction) -> Unit
) {
    val context = LocalContext.current
    var infoDialogTitle by remember { mutableStateOf<String?>(null) }
    var infoDialogText by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 32.dp, bottom = 16.dp, start = 16.dp, end=16.dp)
    ) {
        // Header
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(24.dp))

        // 1. TMDB API Key Section
        ApiKeySection(
            apiKeyInput = state.apiKeyInput,
            isApiKeySaved = state.isApiKeySaved,
            onApiKeyChange = { onAction(SettingsAction.OnApiKeyInputChange(it)) },
            onSave = { onAction(SettingsAction.OnSaveApiKey) },
            onInfoClick = {
                infoDialogTitle = "TMDB API Key"
                infoDialogText = "You will need to bring your API Key for this app to work. Getting a key is easy, go to https://www.themoviedb.org/settings/api and create an account and paste in the API key you get from there"
            }
        )

        Spacer(modifier = Modifier.height(20.dp))
        SectionDivider()
        Spacer(modifier = Modifier.height(20.dp))

        // 2. Library Folders Section
        SectionHeader(
            title = "Library Folders", 
            icon = Icons.Filled.Folder,
            onInfoClick = {
                infoDialogTitle = "Library Folders"
                infoDialogText = "Store your media in 3 different folders and pick them from here. Folder name containing episodes must be named after the show/anime. Name of the episodes inside must be detectable from the filename. Movie files should be named after movie name."
            }
        )
        Spacer(modifier = Modifier.height(12.dp))

        FolderPickerRow(
            label = "Movies",
            currentUri = state.moviesFolderUri,
            onFolderSelected = { uri ->
                FolderPickerHelper.takePersistablePermission(context.contentResolver, uri)
                onAction(SettingsAction.OnMoviesFolderSelected(uri.toString()))
            }
        )
        Spacer(modifier = Modifier.height(8.dp))

        FolderPickerRow(
            label = "TV Shows",
            currentUri = state.showsFolderUri,
            onFolderSelected = { uri ->
                FolderPickerHelper.takePersistablePermission(context.contentResolver, uri)
                onAction(SettingsAction.OnShowsFolderSelected(uri.toString()))
            }
        )
        Spacer(modifier = Modifier.height(8.dp))

        FolderPickerRow(
            label = "Anime",
            currentUri = state.animeFolderUri,
            onFolderSelected = { uri ->
                FolderPickerHelper.takePersistablePermission(context.contentResolver, uri)
                onAction(SettingsAction.OnAnimeFolderSelected(uri.toString()))
            }
        )

        Spacer(modifier = Modifier.height(20.dp))
        SectionDivider()
        Spacer(modifier = Modifier.height(20.dp))



        // 4. Scan Library Section
        SectionHeader(
            title = "Scan", 
            icon = Icons.Filled.Refresh,
            onInfoClick = {
                infoDialogTitle = "Scan"
                infoDialogText = "First scan might take a long time if you have a lot of files. If it doesn't detect everything run it again once."
            }
        )
        Spacer(modifier = Modifier.height(12.dp))

        ScanSection(
            isScanning = state.isScanning,
            scanProgress = state.scanProgress,
            lastScanTime = state.lastScanTime,
            onScan = { onAction(SettingsAction.OnScanLibrary) }
        )

        Spacer(modifier = Modifier.height(20.dp))
        SectionDivider()
        Spacer(modifier = Modifier.height(20.dp))

        // 5. Clear Watch History
        SectionHeader(
            title = "Data", 
            icon = Icons.Filled.DeleteForever,
            onInfoClick = {
                infoDialogTitle = "Data"
                infoDialogText = "If you want to clear app data/cache, please do it via android settings"
            }
        )
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = { onAction(SettingsAction.OnClearHistoryClick) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            )
        ) {
            Text("Clear Watch History")
        }

        Spacer(modifier = Modifier.height(32.dp))

        // 6. App Version
        Text(
            text = "LionLibrary V0.1",
            style = MaterialTheme.typography.bodySmall,
            color = TextTertiary,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Spacer(modifier = Modifier.height(16.dp))
    }

    // Clear History Confirmation Dialog
    if (state.showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { onAction(SettingsAction.OnDismissClearHistoryDialog) },
            title = { Text("Clear Watch History") },
            text = { Text("Clear all your watching progress? This includes all your episode progress, not just continue watching ones") },
            confirmButton = {
                TextButton(
                    onClick = { onAction(SettingsAction.OnConfirmClearHistory) },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { onAction(SettingsAction.OnDismissClearHistoryDialog) }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Info Dialog
    if (infoDialogTitle != null && infoDialogText != null) {
        AlertDialog(
            onDismissRequest = { 
                infoDialogTitle = null
                infoDialogText = null 
            },
            title = { Text(infoDialogTitle!!) },
            text = { Text(infoDialogText!!) },
            confirmButton = {
                TextButton(
                    onClick = { 
                        infoDialogTitle = null
                        infoDialogText = null 
                    }
                ) {
                    Text("Close")
                }
            }
        )
    }
}


@Composable
private fun ApiKeySection(
    apiKeyInput: String,
    isApiKeySaved: Boolean,
    onApiKeyChange: (String) -> Unit,
    onSave: () -> Unit,
    onInfoClick: () -> Unit
) {
    var passwordVisible by remember { mutableStateOf(false) }

    SectionHeader(
        title = "TMDB API Key", 
        icon = Icons.Filled.Key,
        onInfoClick = onInfoClick
    )
    Spacer(modifier = Modifier.height(12.dp))

    OutlinedTextField(
        value = apiKeyInput,
        onValueChange = onApiKeyChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("API Key") },
        placeholder = { Text("Enter your TMDB API key") },
        singleLine = true,
        visualTransformation = if (passwordVisible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        trailingIcon = {
            Row {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Filled.VisibilityOff
                        else Icons.Filled.Visibility,
                        contentDescription = if (passwordVisible) "Hide API key" else "Show API key"
                    )
                }
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = OrangeAccent,
            cursorColor = OrangeAccent,
            focusedLabelColor = OrangeAccent
        )
    )

    Spacer(modifier = Modifier.height(8.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnimatedVisibility(
            visible = isApiKeySaved,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = SuccessGreen,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Saved",
                    style = MaterialTheme.typography.bodySmall,
                    color = SuccessGreen
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onSave,
            enabled = apiKeyInput.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = OrangeAccent)
        ) {
            Text("Save Key")
        }
    }
}

@Composable
private fun FolderPickerRow(
    label: String,
    currentUri: String,
    onFolderSelected: (Uri) -> Unit
) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { onFolderSelected(it) }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (currentUri.isNotBlank()) {
                    Text(
                        text = FolderPickerHelper.getDisplayPath(Uri.parse(currentUri)),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Text(
                        text = "Not configured",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary
                    )
                }
            }
            OutlinedButton(
                onClick = { launcher.launch(null) },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = OrangeAccent)
            ) {
                Text(if (currentUri.isBlank()) "Select" else "Change")
            }
        }
    }
}



@Composable
private fun ScanSection(
    isScanning: Boolean,
    scanProgress: com.example.mediahub.domain.model.ScanProgress?,
    lastScanTime: Long,
    onScan: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Button(
                onClick = onScan,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isScanning,
                colors = ButtonDefaults.buttonColors(containerColor = OrangeAccent)
            ) {
                if (isScanning) {
                    Text("Scanning...")
                } else {
                    Text("Scan Library")
                }
            }

            // Progress indicator
            if (isScanning && scanProgress != null) {
                Spacer(modifier = Modifier.height(12.dp))

                val progress = if (scanProgress.total > 0) {
                    scanProgress.processed.toFloat() / scanProgress.total
                } else 0f

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = OrangeAccent,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "${scanProgress.processed} / ${scanProgress.total} files",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary
                )

                if (scanProgress.currentFile.isNotBlank()) {
                    Text(
                        text = scanProgress.currentFile,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Last scan time
            if (lastScanTime > 0L) {
                Spacer(modifier = Modifier.height(8.dp))
                val dateFormat = remember {
                    SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault())
                }
                Text(
                    text = "Last scan: ${dateFormat.format(Date(lastScanTime))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onInfoClick: (() -> Unit)? = null
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = OrangeAccent,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        if (onInfoClick != null) {
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = onInfoClick,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = "Information about $title",
                    tint = TextTertiary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun SectionDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(DarkSurfaceVariant)
    )
}
