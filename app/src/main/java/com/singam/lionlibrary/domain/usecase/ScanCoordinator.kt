package com.singam.lionlibrary.domain.usecase

import com.singam.lionlibrary.domain.model.ScanProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

enum class ScanTrigger {
    MANUAL,
    AUTO,
}

data class ScanRunState(
    val isScanning: Boolean = false,
    val progress: ScanProgress? = null,
    val trigger: ScanTrigger? = null,
    val summary: com.singam.lionlibrary.domain.model.ScanSummary =
        com.singam.lionlibrary.domain.model.ScanSummary(),
)

sealed interface ScanEvent {
    val trigger: ScanTrigger

    data class Progress(override val trigger: ScanTrigger, val progress: ScanProgress) : ScanEvent

    data class Failed(override val trigger: ScanTrigger, val message: String?) : ScanEvent
}

// Single app-wide owner of library scans, so two scans never run at once.
interface ScanCoordinator {
    val state: StateFlow<ScanRunState>

    // Every progress update, unconflated (for snackbars on terminal statuses).
    val events: Flow<ScanEvent>

    // Returns false if a scan is already running.
    fun startScan(trigger: ScanTrigger): Boolean

    // Runs at most once per process, only when a TMDB key, a folder and internet are present.
    fun autoScanOnLaunch()

    fun cancelScan()
}
