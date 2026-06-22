package com.singam.lionlibrary.presentation.settings

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.singam.lionlibrary.domain.model.ScanProgress
import com.singam.lionlibrary.domain.model.ScanStatus
import com.singam.lionlibrary.domain.repository.SettingsRepository
import com.singam.lionlibrary.domain.repository.WatchProgressRepository
import com.singam.lionlibrary.domain.usecase.ScanLibraryUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch


@Stable
data class SettingsState(
    val apiKey: String = "",
    val apiKeyInput: String = "",
    val moviesFolderUri: String = "",
    val showsFolderUri: String = "",
    val animeFolderUri: String = "",

    val lastScanTime: Long = 0L,
    val isScanning: Boolean = false,
    val scanProgress: ScanProgress? = null,
    val showClearHistoryDialog: Boolean = false,
    val isApiKeySaved: Boolean = false
)


sealed interface SettingsAction {
    data class OnApiKeyInputChange(val value: String) : SettingsAction
    data object OnSaveApiKey : SettingsAction
    data class OnMoviesFolderSelected(val uri: String) : SettingsAction
    data class OnShowsFolderSelected(val uri: String) : SettingsAction
    data class OnAnimeFolderSelected(val uri: String) : SettingsAction

    data object OnScanLibrary : SettingsAction
    data object OnClearHistoryClick : SettingsAction
    data object OnConfirmClearHistory : SettingsAction
    data object OnDismissClearHistoryDialog : SettingsAction
}


sealed interface SettingsEvent {
    data class ShowSnackbar(val message: String) : SettingsEvent
}


class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val watchProgressRepository: WatchProgressRepository,
    private val scanLibraryUseCase: ScanLibraryUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state = _state.asStateFlow()

    private val _events = Channel<SettingsEvent>()
    val events = _events.receiveAsFlow()

    init {
        loadSettings()
    }

    fun onAction(action: SettingsAction) {
        when (action) {
            is SettingsAction.OnApiKeyInputChange -> {
                _state.update { it.copy(apiKeyInput = action.value, isApiKeySaved = false) }
            }
            is SettingsAction.OnSaveApiKey -> saveApiKey()
            is SettingsAction.OnMoviesFolderSelected -> setFolder(action.uri) { uri ->
                settingsRepository.setMoviesFolderUri(uri)
                _state.update { it.copy(moviesFolderUri = uri) }
            }
            is SettingsAction.OnShowsFolderSelected -> setFolder(action.uri) { uri ->
                settingsRepository.setShowsFolderUri(uri)
                _state.update { it.copy(showsFolderUri = uri) }
            }
            is SettingsAction.OnAnimeFolderSelected -> setFolder(action.uri) { uri ->
                settingsRepository.setAnimeFolderUri(uri)
                _state.update { it.copy(animeFolderUri = uri) }
            }

            is SettingsAction.OnScanLibrary -> triggerScan()
            is SettingsAction.OnClearHistoryClick -> {
                _state.update { it.copy(showClearHistoryDialog = true) }
            }
            is SettingsAction.OnConfirmClearHistory -> clearHistory()
            is SettingsAction.OnDismissClearHistoryDialog -> {
                _state.update { it.copy(showClearHistoryDialog = false) }
            }
        }
    }

    private fun loadSettings() {
        viewModelScope.launch {
            val apiKey = settingsRepository.tmdbApiKey.first()
            val moviesFolderUri = settingsRepository.moviesFolderUri.first()
            val showsFolderUri = settingsRepository.showsFolderUri.first()
            val animeFolderUri = settingsRepository.animeFolderUri.first()

            val lastScanTime = settingsRepository.lastScanTime.first()

            _state.update {
                it.copy(
                    apiKey = apiKey,
                    apiKeyInput = apiKey,
                    moviesFolderUri = moviesFolderUri,
                    showsFolderUri = showsFolderUri,
                    animeFolderUri = animeFolderUri,

                    lastScanTime = lastScanTime,
                    isApiKeySaved = apiKey.isNotBlank()
                )
            }
        }
    }

    private fun saveApiKey() {
        viewModelScope.launch {
            val key = _state.value.apiKeyInput.trim()
            settingsRepository.setTmdbApiKey(key)
            _state.update { it.copy(apiKey = key, isApiKeySaved = true) }
            _events.send(SettingsEvent.ShowSnackbar("API key saved"))
        }
    }

    private fun setFolder(uri: String, save: suspend (String) -> Unit) {
        viewModelScope.launch { save(uri) }
    }



    private fun triggerScan() {
        if (_state.value.isScanning) return

        viewModelScope.launch {
            _state.update { it.copy(isScanning = true, scanProgress = null) }

            scanLibraryUseCase()
                .catch { e ->
                    _state.update { it.copy(isScanning = false) }
                    _events.send(SettingsEvent.ShowSnackbar("Scan failed: ${e.message}"))
                }
                .collect { progress ->
                    _state.update { it.copy(scanProgress = progress) }

                    if (progress.status == ScanStatus.COMPLETE) {
                        _state.update {
                            it.copy(
                                isScanning = false,
                                lastScanTime = System.currentTimeMillis()
                            )
                        }
                        _events.send(
                            SettingsEvent.ShowSnackbar(
                                "Scan complete: ${progress.total} files processed"
                            )
                        )
                    } else if (progress.status == ScanStatus.API_KEY_MISSING) {
                        _state.update { it.copy(isScanning = false) }
                        _events.send(SettingsEvent.ShowSnackbar("API Key missing. Please configure your TMDB API Key."))
                    } else if (progress.status == ScanStatus.INVALID_API_KEY) {
                        _state.update { it.copy(isScanning = false) }
                        _events.send(SettingsEvent.ShowSnackbar("Invalid API Key. Please check your TMDB API Key."))
                    } else if (progress.status == ScanStatus.NO_INTERNET) {
                        _state.update { it.copy(isScanning = false) }
                        _events.send(SettingsEvent.ShowSnackbar("No internet. Files saved without metadata."))
                    } else if (progress.status == ScanStatus.PERMISSION_REVOKED) {
                        _state.update { it.copy(isScanning = false) }
                        _events.send(SettingsEvent.ShowSnackbar("Folder permission lost. Please re-select folder."))
                    }
                }
        }
    }

    private fun clearHistory() {
        viewModelScope.launch {
            watchProgressRepository.clearAll()
            _state.update { it.copy(showClearHistoryDialog = false) }
            _events.send(SettingsEvent.ShowSnackbar("Watch history cleared"))
        }
    }
}

