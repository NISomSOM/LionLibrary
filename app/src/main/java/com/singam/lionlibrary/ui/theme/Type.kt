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
    size: Int,
    line: Int,
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

val Typography =
    Typography(
        displayLarge = type(48, 56, FontWeight.Bold),
        displayMedium = type(40, 48, FontWeight.Bold),
        displaySmall = type(32, 40, FontWeight.Bold),
        headlineLarge = type(38, 42, FontWeight.Bold, -1.2f),
        headlineMedium = type(26, 34, FontWeight.SemiBold),
        headlineSmall = type(22, 30, FontWeight.SemiBold),
        titleLarge = type(20, 26, FontWeight.SemiBold),
        titleMedium = type(18, 24, FontWeight.SemiBold),
        titleSmall = type(15, 22, FontWeight.SemiBold),
        bodyLarge = type(15, 22),
        bodyMedium = type(14, 22),
        bodySmall = type(13, 18),
        labelLarge = type(14, 18, FontWeight.SemiBold),
        labelMedium = type(12, 16, FontWeight.Medium),
        labelSmall = type(12, 16, FontWeight.Medium),
    )
