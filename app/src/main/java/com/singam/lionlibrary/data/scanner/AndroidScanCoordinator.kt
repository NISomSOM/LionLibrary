package com.singam.lionlibrary.data.scanner

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.singam.lionlibrary.domain.model.*
import com.singam.lionlibrary.domain.repository.MetadataRepository
import com.singam.lionlibrary.domain.repository.SettingsRepository
import com.singam.lionlibrary.domain.usecase.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*

/** App-lifetime owner: cancellation flushes completed scanner writes before releasing the run. */
class AndroidScanCoordinator(
    private val context: Context,
    private val scanLibraryUseCase: ScanLibraryUseCase,
    private val settingsRepository: SettingsRepository,
    private val metadataRepository: MetadataRepository,
) : ScanCoordinator {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val running = AtomicBoolean(false)
    private val autoAttempted = AtomicBoolean(false)
    private val ready = CompletableDeferred<Unit>()
    private var job: Job? = null
    private val mutableState = MutableStateFlow(ScanRunState())
    override val state = mutableState.asStateFlow()
    private val mutableEvents =
        MutableSharedFlow<ScanEvent>(
            extraBufferCapacity = 256,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    override val events = mutableEvents.asSharedFlow()

    init {
        scope.launch {
            try {
                val previous = settingsRepository.scanSummary.first()
                val summary =
                    if (previous.wasRunning)
                        previous.copy(wasRunning = false, outcome = "INTERRUPTED")
                    else previous
                mutableState.update { it.copy(summary = summary) }
                if (previous.wasRunning) settingsRepository.setScanSummary(summary)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.update {
                    it.copy(summary = ScanSummary(failed = 1, outcome = "ERROR"))
                }
            } finally {
                ready.complete(Unit)
            }
        }
    }

    @Synchronized
    override fun startScan(trigger: ScanTrigger): Boolean {
        if (!running.compareAndSet(false, true)) return false
        mutableState.update { it.copy(isScanning = true, progress = null, trigger = trigger) }
        job =
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                var summary = ScanSummary(wasRunning = true)
                try {
                    ready.await()
                    settingsRepository.setScanSummary(summary)
                    scanLibraryUseCase().collect { progress ->
                        if (progress.status == ScanStatus.COMPLETE) {
                            mutableState.update {
                                it.copy(progress = progress.copy(status = ScanStatus.SCANNING))
                            }
                            when (val recovery = metadataRepository.refreshArtwork()) {
                                is OperationResult.Success -> Unit
                                is OperationResult.Failure -> {
                                    summary = summary.copy(failed = summary.failed + 1)
                                    mutableEvents.tryEmit(ScanEvent.Failed(trigger, null))
                                }
                            }
                        }
                        summary =
                            when (progress.status) {
                                ScanStatus.MATCHED -> summary.copy(matched = summary.matched + 1)
                                ScanStatus.UNIDENTIFIED ->
                                    summary.copy(unidentified = summary.unidentified + 1)
                                ScanStatus.SKIPPED -> summary.copy(skipped = summary.skipped + 1)
                                ScanStatus.ERROR -> summary.copy(failed = summary.failed + 1)
                                else -> summary
                            }
                        if (progress.status in terminalStatuses)
                            summary = summary.copy(outcome = progress.status.name)
                        mutableState.update { it.copy(progress = progress, summary = summary) }
                        mutableEvents.tryEmit(ScanEvent.Progress(trigger, progress))
                        val finished =
                            summary.matched +
                                summary.unidentified +
                                summary.skipped +
                                summary.failed
                        if (
                            (finished > 0 && finished % 20 == 0) ||
                                progress.status in terminalStatuses
                        )
                            settingsRepository.setScanSummary(summary)
                    }
                } catch (cancelled: CancellationException) {
                    summary = summary.copy(outcome = "CANCELLED")
                    throw cancelled
                } catch (_: Exception) {
                    summary = summary.copy(failed = summary.failed + 1, outcome = "ERROR")
                    mutableEvents.tryEmit(ScanEvent.Failed(trigger, null))
                } finally {
                    withContext(NonCancellable) {
                        ready.await()
                        summary =
                            summary.copy(
                                wasRunning = false,
                                finishedAt = System.currentTimeMillis(),
                            )
                        try {
                            settingsRepository.setScanSummary(summary)
                        } catch (_: Exception) {
                            summary = summary.copy(failed = summary.failed + 1, outcome = "ERROR")
                            mutableEvents.tryEmit(ScanEvent.Failed(trigger, null))
                        } finally {
                            synchronized(this@AndroidScanCoordinator) {
                                running.set(false)
                                mutableState.update {
                                    it.copy(isScanning = false, summary = summary)
                                }
                            }
                        }
                    }
                }
            }
        job?.start()
        return true
    }

    @Synchronized
    override fun cancelScan() {
        job?.cancel()
    }

    override fun autoScanOnLaunch() {
        if (!autoAttempted.compareAndSet(false, true)) return
        scope.launch {
            try {
                ready.await()
                if (settingsRepository.tmdbApiKey.first().isBlank()) return@launch
                if (
                    settingsRepository.moviesFolderUri.first().isBlank() &&
                        settingsRepository.showsFolderUri.first().isBlank()
                )
                    return@launch
                val cm = context.getSystemService(ConnectivityManager::class.java) ?: return@launch
                val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return@launch
                if (
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                )
                    startScan(ScanTrigger.AUTO)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableEvents.tryEmit(ScanEvent.Failed(ScanTrigger.AUTO, null))
            }
        }
    }

    private companion object {
        val terminalStatuses =
            setOf(
                ScanStatus.COMPLETE,
                ScanStatus.INVALID_API_KEY,
                ScanStatus.API_KEY_MISSING,
                ScanStatus.NO_INTERNET,
                ScanStatus.PERMISSION_REVOKED,
            )
    }
}
