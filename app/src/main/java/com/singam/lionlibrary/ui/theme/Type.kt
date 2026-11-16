package com.singam.lionlibrary.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.singam.lionlibrary.R

// Static instances of official OFL Inter: correct weights on every supported Android version.
val Inter =
    FontFamily(
        Font(R.font.inter_regular, FontWeight.Normal),
        Font(R.font.inter_medium, FontWeight.Medium),
        Font(R.font.inter_semibold, FontWeight.SemiBold),
        Font(R.font.inter_bold, FontWeight.Bold),
    )

private fun type(
    size: Float,
    line: Float,
    weight: FontWeight = FontWeight.Normal,
    tracking: Float = 0f,
) =
    TextStyle(
        fontFamily = Inter,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = line.sp,
        letterSpacing = tracking.sp,
    )

// Compact, Nuvio-like scale: smaller body copy, bold tight headings and slightly tracked labels.
// Sizes respect the user's system font scale, so every value here is the 1.0x size.
val Typography =
    Typography(
        displayLarge = type(40f, 46f, FontWeight.Bold, -1f),
        displayMedium = type(34f, 40f, FontWeight.Bold, -.8f),
        displaySmall = type(28f, 34f, FontWeight.Bold, -.6f),
        headlineLarge = type(30f, 34f, FontWeight.Bold, -.8f),
        headlineMedium = type(22f, 28f, FontWeight.Bold, -.3f),
        headlineSmall = type(19f, 24f, FontWeight.Bold, -.2f),
        titleLarge = type(16f, 21f, FontWeight.SemiBold, -.1f),
        titleMedium = type(15f, 20f, FontWeight.Bold, -.2f),
        titleSmall = type(13f, 18f, FontWeight.SemiBold),
        bodyLarge = type(13.5f, 19f),
        bodyMedium = type(12.5f, 18f),
        bodySmall = type(11.5f, 15f),
        labelLarge = type(13f, 16f, FontWeight.SemiBold, .1f),
        labelMedium = type(11f, 14f, FontWeight.Medium, .2f),
        labelSmall = type(10f, 13f, FontWeight.Medium, .4f),
    )
