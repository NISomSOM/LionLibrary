package com.singam.lionlibrary.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ScanSummary(
    val matched: Int = 0,
    val unidentified: Int = 0,
    val skipped: Int = 0,
    val failed: Int = 0,
    val finishedAt: Long = 0,
    val outcome: String = "",
    val wasRunning: Boolean = false,
)
