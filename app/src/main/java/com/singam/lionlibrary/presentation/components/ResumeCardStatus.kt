package com.singam.lionlibrary.presentation.components

import com.singam.lionlibrary.domain.model.JumpBackInItem
import kotlin.math.roundToInt

internal sealed interface ResumeCardStatus {
    data object NextUp : ResumeCardStatus

    data class Remaining(val minutes: Long) : ResumeCardStatus

    data class Watched(val percent: Int) : ResumeCardStatus
}

/** Existing captured progress is the only source of time; metadata runtime is not a guess. */
internal fun JumpBackInItem.resumeCardStatus(): ResumeCardStatus {
    val fraction = progress?.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
    if (isNextUp || fraction <= 0f) return ResumeCardStatus.NextUp
    if (durationMs <= 0L)
        return ResumeCardStatus.Watched((fraction * 100).roundToInt().coerceIn(1, 99))
    val position =
        if (positionMs > 0L) positionMs.coerceAtMost(durationMs)
        else (durationMs * fraction.toDouble()).toLong().coerceIn(0L, durationMs)
    return ResumeCardStatus.Remaining(((durationMs - position) / 60_000L).coerceAtLeast(1L))
}
