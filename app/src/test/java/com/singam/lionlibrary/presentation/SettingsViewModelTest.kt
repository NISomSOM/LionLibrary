package com.singam.lionlibrary.presentation

import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.*
import com.singam.lionlibrary.R
import com.singam.lionlibrary.domain.model.DataError
import com.singam.lionlibrary.domain.model.OperationResult
import com.singam.lionlibrary.domain.model.ScanSummary
import com.singam.lionlibrary.domain.usecase.ScanRunState
import com.singam.lionlibrary.domain.usecase.ScanTrigger
import com.singam.lionlibrary.presentation.common.UiText
import com.singam.lionlibrary.presentation.settings.SettingsAction
import com.singam.lionlibrary.presentation.settings.SettingsEvent
import com.singam.lionlibrary.presentation.settings.SettingsViewModel
import com.singam.lionlibrary.testing.*
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Break vectors: whitespace or emoji around a pasted key, a double tap starting two verifications,
 * offline treated as invalid (or as verified), an invalid key being saved, editing the key keeping
 * the verified badge, a manual scan started while one runs, a cancelled/failed scan moving the
 * "last scan" time, and a failing DataStore write flipping a switch that was never saved.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val settings = FakeSettingsRepository()
    private val history = FakeWatchProgressRepository()
    private val metadata = FakeMetadataRepository()
    private val scans = FakeScanCoordinator()

    @BeforeEach fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterEach fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = SettingsViewModel(settings, history, metadata, scans)

    @Test
    fun `stored settings are loaded into state`() =
        runTest(dispatcher) {
            settings.tmdbApiKey.value = "abc"
            settings.keyVerified.value = true
            settings.moviesFolderUri.value = "content://tree/movies"
            settings.forceLibVlc.value = true
            settings.onlineSkipTimes.value = false

            val state = viewModel().also { it.viewModelScope.cancel() }.state.value

            assertThat(state.apiKeyInput).isEqualTo("abc")
            assertThat(state.isApiKeySaved).isTrue()
            assertThat(state.keyVerified).isTrue()
            assertThat(state.moviesFolderUri).isEqualTo("content://tree/movies")
            assertThat(state.forceLibVlc).isTrue()
            assertThat(state.onlineSkipTimes).isFalse()
        }

    @Test
    fun `verified key is trimmed, saved and announced`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.events.test {
                vm.onAction(SettingsAction.OnApiKeyInputChange("  key🔑 \n"))
                vm.onAction(SettingsAction.OnSaveApiKey)
                assertThat(awaitItem())
                    .isEqualTo(SettingsEvent.ShowSnackbar(UiText.Resource(R.string.key_verified)))
            }
            assertThat(metadata.validated).containsExactly("key🔑")
            assertThat(settings.tmdbApiKey.value).isEqualTo("key🔑")
            assertThat(settings.keyVerified.value).isTrue()
            assertThat(vm.state.value.keyChecking).isFalse()
            vm.viewModelScope.cancel()
        }

    @Test
    fun `offline verification saves the key as unverified`() =
        runTest(dispatcher) {
            metadata.validation = OperationResult.Failure(DataError.OFFLINE)
            val vm = viewModel()
            vm.events.test {
                vm.onAction(SettingsAction.OnApiKeyInputChange("key"))
                vm.onAction(SettingsAction.OnSaveApiKey)
                assertThat(awaitItem())
                    .isEqualTo(SettingsEvent.ShowSnackbar(UiText.Resource(R.string.key_unverified)))
            }
            assertThat(settings.tmdbApiKey.value).isEqualTo("key")
            assertThat(settings.keyVerified.value).isFalse()
            assertThat(vm.state.value.isApiKeySaved).isTrue()
            vm.viewModelScope.cancel()
        }

    @Test
    fun `rejected key is not saved and shows the key error`() =
        runTest(dispatcher) {
            metadata.validation = OperationResult.Failure(DataError.UNAUTHORIZED)
            val vm = viewModel()
            vm.events.test {
                vm.onAction(SettingsAction.OnApiKeyInputChange("bad"))
                vm.onAction(SettingsAction.OnSaveApiKey)
                assertThat(awaitItem())
                    .isEqualTo(SettingsEvent.ShowSnackbar(UiText.Resource(R.string.error_api_key)))
            }
            assertThat(settings.tmdbApiKey.value).isEmpty()
            assertThat(vm.state.value.isApiKeySaved).isFalse()
            vm.viewModelScope.cancel()
        }

    @Test
    fun `double tap while verifying starts one check`() =
        runTest(dispatcher) {
            val gate = CompletableDeferred<Unit>()
            metadata.gate = gate
            val vm = viewModel()
            vm.onAction(SettingsAction.OnApiKeyInputChange("key"))

            vm.onAction(SettingsAction.OnSaveApiKey)
            vm.onAction(SettingsAction.OnSaveApiKey)
            assertThat(vm.state.value.keyChecking).isTrue()
            vm.events.test {
                gate.complete(Unit)
                assertThat(awaitItem())
                    .isEqualTo(SettingsEvent.ShowSnackbar(UiText.Resource(R.string.key_verified)))
            }

            assertThat(metadata.validated).containsExactly("key")
            assertThat(vm.state.value.keyChecking).isFalse()
            vm.viewModelScope.cancel()
        }

    @Test
    fun `editing a verified key clears the verified badge`() =
        runTest(dispatcher) {
            settings.tmdbApiKey.value = "abc"
            settings.keyVerified.value = true
            val vm = viewModel()

            vm.onAction(SettingsAction.OnApiKeyInputChange("abcd"))

            assertThat(vm.state.value.keyVerified).isFalse()
            assertThat(vm.state.value.isApiKeySaved).isFalse()
            vm.viewModelScope.cancel()
        }

    @Test
    fun `manual scan is ignored while a scan runs`() =
        runTest(dispatcher) {
            val vm = viewModel()
            scans.state.value = ScanRunState(isScanning = true, trigger = ScanTrigger.AUTO)

            vm.onAction(SettingsAction.OnScanLibrary)
            scans.state.value = ScanRunState()
            vm.onAction(SettingsAction.OnScanLibrary)

            assertThat(scans.started).containsExactly(ScanTrigger.MANUAL)
            vm.viewModelScope.cancel()
        }

    @Test
    fun `only a completed scan moves the last scan time`() =
        runTest(dispatcher) {
            settings.lastScanTime.value = 100
            val vm = viewModel()

            scans.state.value =
                ScanRunState(summary = ScanSummary(finishedAt = 500, outcome = "CANCELLED"))
            assertThat(vm.state.value.lastScanTime).isEqualTo(100L)
            scans.state.value =
                ScanRunState(summary = ScanSummary(finishedAt = 900, outcome = "COMPLETE"))

            assertThat(vm.state.value.lastScanTime).isEqualTo(900L)
            vm.viewModelScope.cancel()
        }

    @Test
    fun `failed preference write keeps the switch and reports storage`() =
        runTest(dispatcher) {
            val vm = viewModel()
            settings.writeFailure = IOException("disk full")
            vm.events.test {
                vm.onAction(SettingsAction.OnForceExternalPlayerToggle(true))
                assertThat(awaitItem())
                    .isEqualTo(SettingsEvent.ShowSnackbar(UiText.Resource(R.string.error_storage)))
            }
            assertThat(vm.state.value.forceExternalPlayer).isFalse()
            vm.viewModelScope.cancel()
        }

    @Test
    fun `confirming clear history clears it and closes the dialog`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.onAction(SettingsAction.OnClearHistoryClick)
            assertThat(vm.state.value.showClearHistoryDialog).isTrue()

            vm.events.test {
                vm.onAction(SettingsAction.OnConfirmClearHistory)
                assertThat(awaitItem())
                    .isEqualTo(
                        SettingsEvent.ShowSnackbar(UiText.Resource(R.string.history_cleared))
                    )
            }
            assertThat(history.calls).containsExactly("clear")
            assertThat(vm.state.value.showClearHistoryDialog).isFalse()
            vm.viewModelScope.cancel()
        }
}
