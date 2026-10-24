package com.singam.lionlibrary.presentation.player

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.singam.lionlibrary.R
import com.singam.lionlibrary.domain.model.SkipSegmentType
import com.singam.lionlibrary.ui.theme.LionLibraryTheme

/** "Skip intro" / "Skip credits" pill in the brand accent; a 48 dp target with its own label. */
@Composable
internal fun PlayerSkipButton(
    type: SkipSegmentType,
    onSkipClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label =
        stringResource(
            when (type) {
                SkipSegmentType.INTRO -> R.string.skip_intro
                SkipSegmentType.CREDITS -> R.string.skip_credits
            }
        )
    val description =
        stringResource(
            when (type) {
                SkipSegmentType.INTRO -> R.string.cd_skip_intro
                SkipSegmentType.CREDITS -> R.string.cd_skip_credits
            }
        )
    Button(
        onClick = onSkipClick,
        modifier =
            modifier.heightIn(min = 48.dp).widthIn(max = 280.dp).semantics {
                contentDescription = description
            },
        colors =
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        contentPadding = PaddingValues(start = 16.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Icon(Icons.Rounded.SkipNext, contentDescription = null, Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Preview(name = "Skip intro")
@Composable
private fun PlayerSkipIntroPreview() {
    LionLibraryTheme { PlayerSkipButton(SkipSegmentType.INTRO, onSkipClick = {}) }
}

@Preview(name = "Skip credits")
@Composable
private fun PlayerSkipCreditsPreview() {
    LionLibraryTheme { PlayerSkipButton(SkipSegmentType.CREDITS, onSkipClick = {}) }
}

@Preview(name = "Skip intro, large font", fontScale = 2f)
@Composable
private fun PlayerSkipLargeFontPreview() {
    LionLibraryTheme { PlayerSkipButton(SkipSegmentType.INTRO, onSkipClick = {}) }
}
