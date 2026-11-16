package com.singam.lionlibrary.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp

/**
 * Top-lit card edge for artwork: a hairline stroke that is brightest along the top and fades out by
 * the bottom, plus a faint sheen over the top third. Drawn over the content, so it never changes
 * layout or clipping (corners still clip artwork only).
 */
fun Modifier.cardEdge(radius: Dp, colors: LionColors, width: Dp = LionLayout.Hairline): Modifier =
    drawWithCache {
        val stroke = width.toPx()
        val corner = CornerRadius(radius.toPx() - stroke / 2)
        val edge = Brush.verticalGradient(0f to colors.cardEdgeTop, .6f to Color.Transparent)
        val sheen = Brush.verticalGradient(0f to colors.cardSheen, .35f to Color.Transparent)
        onDrawWithContent {
            drawContent()
            drawRect(sheen)
            drawRoundRect(
                brush = edge,
                topLeft = Offset(stroke / 2, stroke / 2),
                size = Size(size.width - stroke, size.height - stroke),
                cornerRadius = corner,
                style = Stroke(stroke),
            )
        }
    }
