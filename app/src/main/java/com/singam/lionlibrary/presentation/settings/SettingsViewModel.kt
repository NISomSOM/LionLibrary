package com.singam.lionlibrary.presentation.settings

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.singam.lionlibrary.R
import com.singam.lionlibrary.domain.model.*
import com.singam.lionlibrary.domain.model.ScanProgress
import com.singam.lionlibrary.domain.repository.MetadataRepository
import com.singam.lionlibrary.domain.repository.SettingsRepository
import com.singam.lionlibrary.domain.repository.WatchProgressRepository
import com.singam.lionlibrary.domain.usecase.ScanCoordinator
import com.singam.lionlibrary.domain.usecase.ScanTrigger
import com.singam.lionlibrary.presentation.common.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Stable
data class SettingsState(
    val apiKey: String = "",
    val keyVerified: Boolean = false,
    val keyChecking: Boolean = false,
    val scanRun: com.singam.lionlibrary.domain.usecase.ScanRunState =
        com.singam.lionlibrary.domain.usecase.ScanRunState(),
    val apiKeyInput: String = "",
    val moviesFolderUri: String = "",
    val showsFolderUri: String = "",
    val lastScanTime: Long = 0L,
    val isScanning: Boolean = false,
    val scanProgress: ScanProgress? = null,
    val showClearHistoryDialog: Boolean = false,
    val isApiKeySaved: Boolean = false,
    val forceLibVlc: Boolean = false,
    val forceExternalPlayer: Boolean = false,
)

sealed interface SettingsAction {
    data class PlatformFailure(val error: DataError) : SettingsAction

    data class OnApiKeyInputChange(val value: String) : SettingsAction

    data object OnSaveApiKey : SettingsAction

    data class OnMoviesFolderSelected(val uri: String) : SettingsAction

    data class OnShowsFolderSelected(val uri: String) : SettingsAction

    data object OnCancelScan : SettingsAction

    data object OnScanLibrary : SettingsAction

    data object OnClearHistoryClick : SettingsAction

    data object OnConfirmClearHistory : SettingsAction

    data object OnDismissClearHistoryDialog : SettingsAction

    data class OnForceLibVlcToggle(val enabled: Boolean) : SettingsAction

    data class OnForceExternalPlayerToggle(val enabled: Boolean) : SettingsAction
}

sealed interface SettingsEvent {
    data class ShowSnackbar(val message: UiText) : SettingsEvent
}

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val watchProgressRepository: WatchProgressRepository,
    private val metadataRepository: MetadataRepository,
    private val scanCoordinator: ScanCoordinator,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state = _state.asStateFlow()

    private val _events = Channel<SettingsEvent>()
    val events = _events.receiveAsFlow()

    init {
        loadSettings()
        observeScans()
    }

    fun onAction(action: SettingsAction) {
        when (action) {
            is SettingsAction.PlatformFailure ->
                launchSetting { _events.send(SettingsEvent.ShowSnackbar(action.error.toUiText())) }
            is SettingsAction.OnApiKeyInputChange -> {
                _state.update {
                    it.copy(apiKeyInput = action.value, isApiKeySaved = false, keyVerified = false)
                }
            }
            is SettingsAction.OnSaveApiKey -> saveApiKey()
            is SettingsAction.OnMoviesFolderSelected ->
                setFolder(action.uri) { uri ->
                    settingsRepository.setMoviesFolderUri(uri)
                    _state.update { it.copy(moviesFolderUri = uri) }
                }
            is SettingsAction.OnShowsFolderSelected ->
                setFolder(action.uri) { uri ->
                    settingsRepository.setShowsFolderUri(uri)
                    _state.update { it.copy(showsFolderUri = uri) }
                }

            is SettingsAction.OnCancelScan -> scanCoordinator.cancelScan()
            is SettingsAction.OnScanLibrary -> triggerScan()
            is SettingsAction.OnClearHistoryClick -> {
                _state.update { it.copy(showClearHistoryDialog = true) }
            }
            is SettingsAction.OnConfirmClearHistory -> clearHistory()
            is SettingsAction.OnDismissClearHistoryDialog -> {
                _state.update { it.copy(showClearHistoryDialog = false) }
            }
            is SettingsAction.OnForceLibVlcToggle -> toggleForceLibVlc(action.enabled)
            is SettingsAction.OnForceExternalPlayerToggle ->
                toggleForceExternalPlayer(action.enabled)
        }
    }

    private fun launchSetting(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _events.send(SettingsEvent.ShowSnackbar(DataError.STORAGE.toUiText()))
            }
        }
    }

    private fun loadSettings() {
        launchSetting {
            val apiKey = settingsRepository.tmdbApiKey.first()
            val moviesFolderUri = settingsRepository.moviesFolderUri.first()
            val showsFolderUri = settingsRepository.showsFolderUri.first()

            val lastScanTime = settingsRepository.lastScanTime.first()
            val verified = settingsRepository.keyVerified.first()

            _state.update {
                it.copy(
                    apiKey = apiKey,
                    apiKeyInput = apiKey,
                    moviesFolderUri = moviesFolderUri,
                    showsFolderUri = showsFolderUri,
                    lastScanTime = lastScanTime,
                    keyVerified = verified,
                    isApiKeySaved = apiKey.isNotBlank(),
                )
            }

            settingsRepository.forceLibVlc.first().let { forced ->
                _state.update { it.copy(forceLibVlc = forced) }
            }

            settingsRepository.forceExternalPlayer.first().let { forced ->
                _state.update { it.copy(forceExternalPlayer = forced) }
            }
        }
    }

    private fun saveApiKey() {
        if (state.value.keyChecking) return
        val key = state.value.apiKeyInput.trim()
        _state.update { it.copy(keyChecking = true) }
        launchSetting {
            try {
                when (val result = metadataRepository.validateKey(key)) {
                    is OperationResult.Success -> {
                        settingsRepository.setTmdbApiKey(key)
                        settingsRepository.setKeyVerified(true)
                        _state.update {
                            it.copy(
                                apiKey = key,
                                apiKeyInput = key,
                                isApiKeySaved = true,
                                keyVerified = true,
                            )
                        }
                        _events.send(
                            SettingsEvent.ShowSnackbar(UiText.Resource(R.string.key_verified))
                        )
                    }
                    is OperationResult.Failure -> {
                        if (result.error == DataError.OFFLINE) {
                            settingsRepository.setTmdbApiKey(key)
                            _state.update {
                                it.copy(
                                    apiKey = key,
                                    apiKeyInput = key,
                                    isApiKeySaved = true,
                                    keyVerified = false,
                                )
                            }
                            _events.send(
                                SettingsEvent.ShowSnackbar(UiText.Resource(R.string.key_unverified))
                            )
                        } else _events.send(SettingsEvent.ShowSnackbar(result.error.toUiText()))
                    }
                }
            } finally {
                _state.update { it.copy(keyChecking = false) }
            }
        }
    }

    private fun setFolder(uri: String, save: suspend (String) -> Unit) {
        launchSetting { save(uri) }
    }

    private fun triggerScan() {
        if (_state.value.isScanning) return
        scanCoordinator.startScan(ScanTrigger.MANUAL)
    }

    // Scans run in the app-wide coordinator; this mirrors them (manual or auto) into the UI.
    private fun observeScans() {
        launchSetting {
            scanCoordinator.state.collect { run ->
                _state.update {
                    it.copy(
                        isScanning = run.isScanning,
                        scanProgress = run.progress,
                        scanRun = run,
                        lastScanTime =
                            if (run.summary.outcome == "COMPLETE") run.summary.finishedAt
                            else it.lastScanTime,
                    )
                }
            }
        }
    }

    private fun clearHistory() {
        launchSetting {
            watchProgressRepository.clearAll()
            _state.update { it.copy(showClearHistoryDialog = false) }
            _events.send(SettingsEvent.ShowSnackbar(UiText.Resource(R.string.history_cleared)))
        }
    }

    private fun toggleForceLibVlc(enabled: Boolean) {
        launchSetting {
            settingsRepository.setForceLibVlc(enabled)
            _state.update { it.copy(forceLibVlc = enabled) }
        }
    }

    private fun toggleForceExternalPlayer(enabled: Boolean) {
        launchSetting {
            settingsRepository.setForceExternalPlayer(enabled)
            _state.update { it.copy(forceExternalPlayer = enabled) }
        }
    }
}
