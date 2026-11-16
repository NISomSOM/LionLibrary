package com.singam.lionlibrary.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.singam.lionlibrary.ui.theme.LionLayout
import com.singam.lionlibrary.ui.theme.LionLibraryTheme
import com.singam.lionlibrary.ui.theme.LionTheme

/**
 * One row of a long-press options sheet (Jump Back In, episodes, posters). The label is the
 * accessible name; the icon is decorative.
 */
@Composable
internal fun MediaOptionItem(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconTint: Color = LionTheme.colors.onMedia,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = LionLayout.SpaceXl, vertical = LionLayout.SpaceL),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(LionLayout.SpaceXl))
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = LionTheme.colors.onMedia,
        )
    }
}

@Preview(name = "Option")
@Composable
private fun MediaOptionItemPreview() {
    LionLibraryTheme { MediaOptionItem(Icons.Rounded.PlayArrow, "Play from beginning", {}) }
}

@Preview(name = "Destructive option, large font", fontScale = 2f)
@Composable
private fun MediaOptionItemDestructivePreview() {
    LionLibraryTheme {
        MediaOptionItem(
            Icons.Rounded.Delete,
            "Delete from device",
            {},
            iconTint = MaterialTheme.colorScheme.error,
        )
    }
}
