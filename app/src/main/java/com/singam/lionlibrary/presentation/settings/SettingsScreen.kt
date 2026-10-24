package com.singam.lionlibrary.presentation.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.singam.lionlibrary.R
import com.singam.lionlibrary.domain.model.DataError
import com.singam.lionlibrary.presentation.components.PageHeading
import com.singam.lionlibrary.presentation.navigation.LocalFloatingNavigationInset
import com.singam.lionlibrary.ui.theme.LionLibraryTheme
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingsRoot(
    snackbarHostState: SnackbarHostState,
    windowSizeClass: WindowSizeClass,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = androidx.compose.ui.platform.LocalResources.current
    var notices by remember { mutableStateOf<List<LicenseNotice>>(emptyList()) }
    LaunchedEffect(context, resources) {
        notices =
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                listOf(
                        R.string.license_notices to "THIRD_PARTY_NOTICES.md",
                        R.string.license_apache to "licenses/Apache-2.0.txt",
                        R.string.license_vlc to "licenses/LGPL-2.1.txt",
                        R.string.license_font to "licenses/Inter-OFL.txt",
                    )
                    .map { (title, path) ->
                        LicenseNotice(
                            resources.getString(title),
                            context.assets.open(path).bufferedReader().use { it.readText() },
                        )
                    }
            }
    }
    val movies =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri?.let {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        it,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                    viewModel.onAction(SettingsAction.OnMoviesFolderSelected(it.toString()))
                } catch (_: SecurityException) {
                    viewModel.onAction(SettingsAction.PlatformFailure(DataError.PERMISSION))
                }
            }
        }
    val shows =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri?.let {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        it,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                    viewModel.onAction(SettingsAction.OnShowsFolderSelected(it.toString()))
                } catch (_: SecurityException) {
                    viewModel.onAction(SettingsAction.PlatformFailure(DataError.PERMISSION))
                }
            }
        }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is SettingsEvent.ShowSnackbar ->
                    snackbarHostState.showSnackbar(event.message.resolve(context))
            }
        }
    }
    SettingsScreen(
        state = state,
        onAction = viewModel::onAction,
        onMovies = { movies.launch(null) },
        onShows = { shows.launch(null) },
        notices = notices,
    )
}

data class LicenseNotice(val title: String, val text: String)

@Composable
fun SettingsScreen(
    state: SettingsState,
    onAction: (SettingsAction) -> Unit,
    onMovies: () -> Unit,
    onShows: () -> Unit,
    modifier: Modifier = Modifier,
    notices: List<LicenseNotice> = emptyList(),
) {
    var selectedNotice by remember { mutableStateOf<LicenseNotice?>(null) }
    var revealKey by remember { mutableStateOf(false) }
    var showCredits by remember { mutableStateOf(false) }
    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
            )
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = LocalFloatingNavigationInset.current + 24.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(
            Modifier.widthIn(max = 640.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PageHeading(stringResource(R.string.settings))
            SettingsSection(stringResource(R.string.api_key)) {
                OutlinedTextField(
                    value = state.apiKeyInput,
                    onValueChange = { onAction(SettingsAction.OnApiKeyInputChange(it)) },
                    singleLine = true,
                    visualTransformation =
                        if (revealKey) VisualTransformation.None
                        else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.keyChecking,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    trailingIcon = {
                        IconButton(onClick = { revealKey = !revealKey }) {
                            Icon(
                                if (revealKey)
                                    androidx.compose.material.icons.Icons.Default.VisibilityOff
                                else androidx.compose.material.icons.Icons.Default.Visibility,
                                stringResource(
                                    if (revealKey) R.string.hide_key else R.string.show_key
                                ),
                            )
                        }
                    },
                )
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    if (state.isApiKeySaved)
                        Text(
                            stringResource(
                                if (state.keyVerified) R.string.key_verified
                                else R.string.key_unverified
                            ),
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    Button(
                        onClick = { onAction(SettingsAction.OnSaveApiKey) },
                        enabled = !state.keyChecking && state.apiKeyInput.isNotBlank(),
                    ) {
                        Text(stringResource(R.string.save_key))
                    }
                }
                if (state.keyChecking) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            SettingsSection(stringResource(R.string.library_folders)) {
                FolderSetting(
                    stringResource(R.string.movies_folder),
                    state.moviesFolderUri,
                    onMovies,
                )
                FolderSetting(stringResource(R.string.shows_folder), state.showsFolderUri, onShows)
            }
            SettingsSection(stringResource(R.string.playback)) {
                PlaybackSetting(
                    stringResource(R.string.force_vlc),
                    state.forceLibVlc,
                    { onAction(SettingsAction.OnForceLibVlcToggle(it)) },
                    enabled = !state.forceExternalPlayer,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                PlaybackSetting(
                    stringResource(R.string.force_external),
                    state.forceExternalPlayer,
                    { onAction(SettingsAction.OnForceExternalPlayerToggle(it)) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                PlaybackSetting(
                    stringResource(R.string.online_skip_times),
                    state.onlineSkipTimes,
                    { onAction(SettingsAction.OnOnlineSkipTimesToggle(it)) },
                    description = stringResource(R.string.online_skip_times_description),
                )
            }
            SettingsSection(stringResource(R.string.scan_library)) {
                if (state.isScanning) {
                    val progress = state.scanProgress
                    if (
                        progress != null &&
                            progress.total > 0 &&
                            progress.processed < progress.total
                    ) {
                        LinearProgressIndicator(
                            progress = { progress.processed.toFloat() / progress.total },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            progress.currentFile,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                    } else {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(
                            stringResource(R.string.recovering_artwork),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    OutlinedButton(onClick = { onAction(SettingsAction.OnCancelScan) }) {
                        Text(stringResource(R.string.cancel_scan))
                    }
                } else {
                    Button(
                        onClick = { onAction(SettingsAction.OnScanLibrary) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled =
                            state.apiKey.isNotBlank() &&
                                (state.moviesFolderUri.isNotBlank() ||
                                    state.showsFolderUri.isNotBlank()),
                    ) {
                        Text(stringResource(R.string.scan_library))
                    }
                }
                if (state.lastScanTime > 0)
                    Text(
                        stringResource(
                            R.string.last_scan,
                            java.text.DateFormat.getDateTimeInstance()
                                .format(java.util.Date(state.lastScanTime)),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
            }
            OutlinedButton(
                onClick = { onAction(SettingsAction.OnClearHistoryClick) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.clear_history))
            }
            TextButton(onClick = { showCredits = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.about))
            }
        }
    }
    if (state.showClearHistoryDialog)
        AlertDialog(
            onDismissRequest = { onAction(SettingsAction.OnDismissClearHistoryDialog) },
            title = { Text(stringResource(R.string.clear_history)) },
            text = { Text(stringResource(R.string.clear_history_help)) },
            confirmButton = {
                TextButton(onClick = { onAction(SettingsAction.OnConfirmClearHistory) }) {
                    Text(stringResource(R.string.clear_history))
                }
            },
            dismissButton = {
                TextButton(onClick = { onAction(SettingsAction.OnDismissClearHistoryDialog) }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    if (showCredits)
        AlertDialog(
            onDismissRequest = { showCredits = false },
            title = { Text(stringResource(R.string.about)) },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(stringResource(R.string.privacy_summary))
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(R.drawable.tmdb_logo),
                        contentDescription = stringResource(R.string.tmdb_logo_description),
                        modifier = Modifier.width(180.dp).height(24.dp),
                    )
                    Text(stringResource(R.string.tmdb_attribution))
                    Text(stringResource(R.string.skip_times_attribution))
                    Text(stringResource(R.string.third_party_credits))
                    notices.forEach { notice ->
                        TextButton(
                            onClick = {
                                selectedNotice = notice
                                showCredits = false
                            }
                        ) {
                            Text(notice.title)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCredits = false }) {
                    Text(stringResource(R.string.close))
                }
            },
        )
    selectedNotice?.let { notice ->
        AlertDialog(
            onDismissRequest = { selectedNotice = null },
            title = { Text(notice.title) },
            text = {
                Text(
                    notice.text,
                    modifier =
                        Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedNotice = null
                        showCredits = true
                    }
                ) {
                    Text(stringResource(R.string.close))
                }
            },
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        border = BorderStroke(.5.dp, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = .8.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            content()
        }
    }
}

@Composable
private fun FolderSetting(
    title: String,
    uri: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            Modifier.heightIn(min = 64.dp).padding(vertical = 10.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(Modifier.size(36.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Icon(
                        androidx.compose.material.icons.Icons.Outlined.Folder,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    if (uri.isBlank()) stringResource(R.string.choose_folder)
                    else Uri.decode(uri.substringAfterLast('/')).substringAfter(':'),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
            Icon(
                androidx.compose.material.icons.Icons.AutoMirrored.Filled.KeyboardArrowRight,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PlaybackSetting(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    description: String? = null,
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else .45f),
            )
            description?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(
            checked,
            onCheckedChange,
            enabled = enabled,
            modifier = Modifier.semantics { contentDescription = title },
        )
    }
}

@Preview(name = "Settings")
@Preview(name = "Large text", fontScale = 2f)
@Composable
private fun SettingsPreview() {
    LionLibraryTheme { SettingsScreen(SettingsState(), {}, {}, {}) }
}

@Preview(name = "Scanning", widthDp = 840)
@Composable
private fun ScanPreview() {
    LionLibraryTheme { SettingsScreen(SettingsState(isScanning = true), {}, {}, {}) }
}

@Preview(name = "Confirmation")
@Composable
private fun ConfirmPreview() {
    LionLibraryTheme { SettingsScreen(SettingsState(showClearHistoryDialog = true), {}, {}, {}) }
}

@Preview(name = "Folder selected and playback overrides", fontScale = 2f)
@Composable
private fun SettingsRowsPreview() {

    LionLibraryTheme {
        Column(Modifier.padding(16.dp)) {
            FolderSetting("Movies folder", "content://fixture/Movies", {})

            PlaybackSetting("Force libVLC", true, {})

            PlaybackSetting("Force libVLC", false, {}, enabled = false)

            PlaybackSetting("Force external player", false, {})
        }
    }
}
