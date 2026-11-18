package com.singam.lionlibrary.presentation.navigation

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Timing of the poster-to-Details transition. Opening: the poster flies for [openMs]; the page body
 * appears [bodyStartMs] after the tap (while the poster is still landing) in three groups, each
 * fading in over [groupFadeMs] while sliding down [slide] from just under the poster,
 * [groupStaggerMs] apart. Closing: the body fades out over [closeBodyMs] with the same small slide
 * up; the poster holds [closeHoldMs] and then flies home for [closeMs].
 */
@Immutable
data class PosterMotion(
    val openMs: Int,
    val bodyStartMs: Int,
    val groupFadeMs: Int,
    val groupStaggerMs: Int,
    val slide: Dp,
    val closeBodyMs: Int,
    val closeHoldMs: Int,
    val closeMs: Int,
)

/**
 * The owner's choice (2026-11-17) after comparing three presets on the phone: the quickest
 * ("Snappy"). The other two were Balanced (320/280 ms flights, 190 ms body start, 230 ms fades, 40
 * ms stagger) and Calm (380/320, 240, 280, 50).
 */
val PosterTiming =
    PosterMotion(
        openMs = 260,
        bodyStartMs = 150,
        groupFadeMs = 190,
        groupStaggerMs = 30,
        slide = 24.dp,
        closeBodyMs = 100,
        closeHoldMs = 40,
        closeMs = 240,
    )
