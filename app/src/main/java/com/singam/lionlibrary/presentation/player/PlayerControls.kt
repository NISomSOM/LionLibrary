package com.singam.lionlibrary.presentation.player

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.singam.lionlibrary.R

/** Back, in the top-left corner. */
@Composable
internal fun PlayerBackButton(onBackClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier) {
        PlayerTonalIconButton(onClick = onBackClick) {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.back),
            )
        }
    }
}

/** Lock and open-in-external-player in the top-right corner. */
@Composable
internal fun PlayerTopActions(
    isExternalLoading: Boolean,
    onLockClick: () -> Unit,
    onExternalClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PlayerTonalIconButton(onClick = onLockClick) {
            Icon(Icons.Rounded.Lock, contentDescription = stringResource(R.string.ui_lock))
        }
        PlayerTonalIconButton(onClick = onExternalClick, enabled = !isExternalLoading) {
            if (isExternalLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            } else {
                Icon(
                    Icons.AutoMirrored.Rounded.OpenInNew,
                    contentDescription = stringResource(R.string.ui_open_external),
                )
            }
        }
    }
}

/** Rewind, play/pause and forward in the middle of the video. */
@Composable
internal fun PlayerCenterControls(
    isPlaying: Boolean,
    onRewindClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onForwardClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(40.dp),
    ) {
        IconButton(onClick = onRewindClick, colors = onVideoIconColors()) {
            Icon(
                Icons.Rounded.Replay10,
                contentDescription = stringResource(R.string.ui_rewind_10s),
                modifier = Modifier.size(32.dp),
            )
        }
        FilledTonalIconButton(
            onClick = onPlayPauseClick,
            modifier = Modifier.size(64.dp),
            colors =
                IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.scrim.copy(alpha = .45f),
                    contentColor = MaterialTheme.colorScheme.onBackground,
                ),
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription =
                    stringResource(if (isPlaying) R.string.ui_pause else R.string.play),
                modifier = Modifier.size(36.dp),
            )
        }
        IconButton(onClick = onForwardClick, colors = onVideoIconColors()) {
            Icon(
                Icons.Rounded.Forward10,
                contentDescription = stringResource(R.string.ui_forward_10s),
                modifier = Modifier.size(32.dp),
            )
        }
    }
}

/** Shown on tap while the screen is locked; the only control that works then. */
@Composable
internal fun PlayerUnlockButton(onUnlockClick: () -> Unit, modifier: Modifier = Modifier) {
    FilledTonalButton(
        onClick = onUnlockClick,
        modifier = modifier,
        colors =
            ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.scrim.copy(alpha = .6f),
                contentColor = MaterialTheme.colorScheme.onBackground,
            ),
    ) {
        Icon(Icons.Rounded.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.ui_unlock))
    }
}

@Composable
private fun PlayerTonalIconButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
        colors =
            IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.scrim.copy(alpha = .45f),
                contentColor = MaterialTheme.colorScheme.onBackground,
            ),
        content = content,
    )
}

@Composable
private fun onVideoIconColors() =
    IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onBackground)

@androidx.compose.ui.tooling.preview.Preview(name = "Top actions")
@Composable
private fun TopActionsPreview() {
    com.singam.lionlibrary.ui.theme.LionLibraryTheme { PlayerTopActions(false, {}, {}) }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Top actions, opening external")
@Composable
private fun TopActionsLoadingPreview() {
    com.singam.lionlibrary.ui.theme.LionLibraryTheme { PlayerTopActions(true, {}, {}) }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Back")
@Composable
private fun BackButtonPreview() {
    com.singam.lionlibrary.ui.theme.LionLibraryTheme { PlayerBackButton({}) }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Center, playing")
@Composable
private fun CenterPlayingPreview() {
    com.singam.lionlibrary.ui.theme.LionLibraryTheme { PlayerCenterControls(true, {}, {}, {}) }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Center, paused")
@Composable
private fun CenterPausedPreview() {
    com.singam.lionlibrary.ui.theme.LionLibraryTheme { PlayerCenterControls(false, {}, {}, {}) }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Unlock")
@Composable
private fun UnlockPreview() {
    com.singam.lionlibrary.ui.theme.LionLibraryTheme { PlayerUnlockButton({}) }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Unlock, large font", fontScale = 2f)
@Composable
private fun UnlockLargeFontPreview() {
    com.singam.lionlibrary.ui.theme.LionLibraryTheme { PlayerUnlockButton({}) }
}
