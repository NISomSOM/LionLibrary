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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.singam.lionlibrary.R
import com.singam.lionlibrary.ui.theme.LionLibraryTheme

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

/**
 * Rewind, play/pause and forward in the middle of the video: large bare glyphs with no backing
 * disc, so the picture shows through and the play button reads as the focus.
 */
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
        horizontalArrangement = Arrangement.spacedBy(56.dp),
    ) {
        IconButton(
            onClick = onRewindClick,
            colors = onVideoIconColors(),
            modifier = Modifier.size(64.dp),
        ) {
            Icon(
                Icons.Rounded.Replay10,
                contentDescription = stringResource(R.string.ui_rewind_10s),
                modifier = Modifier.size(48.dp),
            )
        }
        IconButton(
            onClick = onPlayPauseClick,
            colors = onVideoIconColors(),
            modifier = Modifier.size(88.dp),
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription =
                    stringResource(if (isPlaying) R.string.ui_pause else R.string.play),
                modifier = Modifier.size(80.dp),
            )
        }
        IconButton(
            onClick = onForwardClick,
            colors = onVideoIconColors(),
            modifier = Modifier.size(64.dp),
        ) {
            Icon(
                Icons.Rounded.Forward10,
                contentDescription = stringResource(R.string.ui_forward_10s),
                modifier = Modifier.size(48.dp),
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

@Preview(name = "Top actions")
@Composable
private fun TopActionsPreview() {
    LionLibraryTheme { PlayerTopActions(false, {}, {}) }
}

@Preview(name = "Top actions, opening external")
@Composable
private fun TopActionsLoadingPreview() {
    LionLibraryTheme { PlayerTopActions(true, {}, {}) }
}

@Preview(name = "Back")
@Composable
private fun BackButtonPreview() {
    LionLibraryTheme { PlayerBackButton({}) }
}

@Preview(name = "Center, playing")
@Composable
private fun CenterPlayingPreview() {
    LionLibraryTheme { PlayerCenterControls(true, {}, {}, {}) }
}

@Preview(name = "Center, paused")
@Composable
private fun CenterPausedPreview() {
    LionLibraryTheme { PlayerCenterControls(false, {}, {}, {}) }
}

@Preview(name = "Unlock")
@Composable
private fun UnlockPreview() {
    LionLibraryTheme { PlayerUnlockButton({}) }
}

@Preview(name = "Unlock, large font", fontScale = 2f)
@Composable
private fun UnlockLargeFontPreview() {
    LionLibraryTheme { PlayerUnlockButton({}) }
}
