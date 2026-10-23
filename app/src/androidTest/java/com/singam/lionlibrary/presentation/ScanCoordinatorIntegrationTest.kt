package com.singam.lionlibrary.presentation

import androidx.test.platform.app.InstrumentationRegistry
import com.singam.lionlibrary.data.scanner.AndroidScanCoordinator
import com.singam.lionlibrary.domain.model.*
import com.singam.lionlibrary.domain.repository.SettingsRepository
import com.singam.lionlibrary.domain.usecase.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test

class ScanCoordinatorIntegrationTest {
    @Test
    fun completionWaitsForArtworkRecoveryAndStillRejectsConcurrentRuns() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val metadata =
            object : com.singam.lionlibrary.domain.repository.MetadataRepository by MetadataFake() {
                override suspend fun refreshArtwork(): OperationResult<Int, DataError> {
                    started.complete(Unit)
                    release.await()
                    return OperationResult.Success(1)
                }
            }
        val coordinator =
            AndroidScanCoordinator(
                InstrumentationRegistry.getInstrumentation().targetContext,
                object : ScanLibraryUseCase {
                    override fun invoke() = flowOf(ScanProgress(1, 1, "", ScanStatus.COMPLETE))
                },
                ScanSettingsFake(),
                metadata,
            )
        assertTrue(coordinator.startScan(ScanTrigger.MANUAL))
        withTimeout(5000) { started.await() }
        assertTrue(coordinator.state.value.isScanning)
        assertFalse(coordinator.startScan(ScanTrigger.MANUAL))
        release.complete(Unit)
        val run = withTimeout(5000) { coordinator.state.first { !it.isScanning } }
        assertEquals("COMPLETE", run.summary.outcome)
    }

    @Test
    fun cancelFlushesAndRejectsConcurrentRequest() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val flushed = CompletableDeferred<Unit>()
        val settings = ScanSettingsFake()
        val coordinator =
            AndroidScanCoordinator(
                InstrumentationRegistry.getInstrumentation().targetContext,
                object : ScanLibraryUseCase {
                    override fun invoke() =
                        flow<ScanProgress> {
                            started.complete(Unit)
                            try {
                                awaitCancellation()
                            } finally {
                                withContext(NonCancellable) {
                                    delay(10)
                                    flushed.complete(Unit)
                                }
                            }
                        }
                },
                settings,
                MetadataFake(),
            )
        assertTrue(coordinator.startScan(ScanTrigger.MANUAL))
        assertFalse(coordinator.startScan(ScanTrigger.MANUAL))
        withTimeout(5000) { started.await() }
        coordinator.cancelScan()
        val result = withTimeout(5000) { coordinator.state.first { !it.isScanning } }
        assertTrue(flushed.isCompleted)
        assertEquals("CANCELLED", result.summary.outcome)
        assertFalse(settings.saved.value.wasRunning)
    }

    @Test
    fun immediateCancellationDoesNotLeaveRunLocked() = runBlocking {
        val settings = ScanSettingsFake()
        val coordinator =
            AndroidScanCoordinator(
                InstrumentationRegistry.getInstrumentation().targetContext,
                object : ScanLibraryUseCase {
                    override fun invoke() = flow<ScanProgress> { awaitCancellation() }
                },
                settings,
                MetadataFake(),
            )
        repeat(5) {
            assertTrue(coordinator.startScan(ScanTrigger.MANUAL))
            coordinator.cancelScan()
            val run = withTimeout(5000) { coordinator.state.first { !it.isScanning } }
            assertFalse(run.summary.wasRunning)
            assertEquals("CANCELLED", run.summary.outcome)
        }
    }

    @Test
    fun previousRunningSummaryIsRecoveredAsInterrupted(): Unit = runBlocking {
        val settings = ScanSettingsFake(ScanSummary(matched = 3, wasRunning = true))
        val coordinator =
            AndroidScanCoordinator(
                InstrumentationRegistry.getInstrumentation().targetContext,
                object : ScanLibraryUseCase {
                    override fun invoke() = emptyFlow<ScanProgress>()
                },
                settings,
                MetadataFake(),
            )
        val run =
            withTimeout(5000) { coordinator.state.first { it.summary.outcome == "INTERRUPTED" } }
        assertEquals(3, run.summary.matched)
        assertFalse(run.summary.wasRunning)
        withTimeout(5000) { settings.saved.first { !it.wasRunning } }
    }
}

private class ScanSettingsFake(initial: ScanSummary = ScanSummary()) :
    SettingsRepository by SettingsFake() {
    val saved = MutableStateFlow(initial)
    override val scanSummary: Flow<ScanSummary> = saved

    override suspend fun setScanSummary(summary: ScanSummary) {
        saved.value = summary
    }
}

internal class MetadataFake : com.singam.lionlibrary.domain.repository.MetadataRepository {
    override suspend fun refreshArtwork(): OperationResult<Int, DataError> =
        OperationResult.Success(0)

    override suspend fun validateKey(key: String): OperationResult<Unit, DataError> =
        error("unused")

    override suspend fun search(
        title: String,
        type: MediaType,
    ): OperationResult<
        List<com.singam.lionlibrary.domain.repository.MetadataCandidate>,
        DataError,
    > = error("unused")

    override suspend fun identify(
        mediaId: Long,
        candidate: com.singam.lionlibrary.domain.repository.MetadataCandidate,
        assignment: com.singam.lionlibrary.domain.repository.EpisodeAssignment?,
    ): OperationResult<Unit, DataError> = error("unused")

    override suspend fun retry(mediaId: Long): OperationResult<Unit, DataError> = error("unused")

    override suspend fun checkAvailability(): OperationResult<Set<Long>, DataError> =
        error("unused")
}
