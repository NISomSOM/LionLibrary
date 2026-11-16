package com.singam.lionlibrary.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.singam.lionlibrary.R
import com.singam.lionlibrary.ui.theme.LionLibraryTheme

// Brand marks keep their own colours in any theme.
internal val ImdbYellow = Color(0xFFF5C518)
private val ImdbInk = Color(0xFF000000)
private val LetterboxdOrange = Color(0xFFFF8000)
internal val LetterboxdGreen = Color(0xFF00E054)
private val LetterboxdBlue = Color(0xFF40BCF4)

/** Yellow IMDb-style tag, drawn as text so no third-party asset is shipped. */
@Composable
internal fun ImdbBadge(modifier: Modifier = Modifier) {
    Text(
        stringResource(R.string.hero_imdb_label),
        modifier =
            modifier
                .background(ImdbYellow, RoundedCornerShape(4.dp))
                .padding(horizontal = 5.dp, vertical = 1.dp),
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
        color = ImdbInk,
        maxLines = 1,
    )
}

/** Letterboxd's three overlapping dots, drawn rather than shipped as an image asset. */
@Composable
internal fun LetterboxdMark(modifier: Modifier = Modifier.height(12.dp).aspectRatio(2.6f)) {
    Canvas(modifier) {
        val radius = size.height / 2f
        val step = (size.width - 2 * radius) / 2f
        listOf(LetterboxdOrange, LetterboxdGreen, LetterboxdBlue).forEachIndexed { index, color ->
            drawCircle(color, radius, Offset(radius + step * index, radius))
        }
    }
}

@Preview(name = "IMDb badge")
@Composable
private fun ImdbBadgePreview() {
    LionLibraryTheme { ImdbBadge() }
}

@Preview(name = "Letterboxd mark")
@Composable
private fun LetterboxdMarkPreview() {
    LionLibraryTheme { LetterboxdMark() }
}
