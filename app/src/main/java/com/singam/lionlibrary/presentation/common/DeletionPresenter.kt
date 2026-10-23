package com.singam.lionlibrary.presentation.common

import com.singam.lionlibrary.R
import com.singam.lionlibrary.domain.model.OperationResult
import com.singam.lionlibrary.domain.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class DeletionUiState(
    val preview: DeletionPreview? = null,
    val preparing: Boolean = false,
    val deleting: Boolean = false,
)

/** Shared MVI operation owner, scoped to its Home/Details ViewModel. */
class DeletionPresenter(
    private val repository: MediaDeletionRepository,
    private val scope: CoroutineScope,
    private val onDeleted: suspend (DeletionOutcome) -> Unit,
    private val onMessage: suspend (UiText) -> Unit,
) {
    private val mutableState = MutableStateFlow(DeletionUiState())
    val state = mutableState.asStateFlow()
    private var job: Job? = null

    fun request(target: DeletionTarget) {
        if (state.value.preparing || state.value.deleting) return
        mutableState.update { DeletionUiState(preparing = true) }
        job = scope.launch {
            try {
                when (val result = repository.preview(target)) {
                    is OperationResult.Success ->
                        mutableState.update { DeletionUiState(preview = result.data) }
                    is OperationResult.Failure -> {
                        mutableState.update { DeletionUiState() }
                        onMessage(result.error.toUiText())
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.update { DeletionUiState() }
                onMessage(DeletionError.UNAVAILABLE.toUiText())
            }
        }
    }

    fun confirm() {
        val preview = state.value.preview ?: return
        if (state.value.deleting) return
        mutableState.update { it.copy(deleting = true) }
        job = scope.launch {
            try {
                val result = repository.delete(preview)
                mutableState.update { DeletionUiState() }
                when (result) {
                    is OperationResult.Success -> {
                        onDeleted(result.data)
                        onMessage(UiText.Resource(R.string.delete_success))
                    }
                    is OperationResult.Failure -> onMessage(result.error.toUiText())
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.update { DeletionUiState() }
                onMessage(DeletionError.DELETE_FAILED.toUiText())
            }
        }
    }

    fun dismiss() {
        if (state.value.deleting) return
        job?.cancel()
        mutableState.update { DeletionUiState() }
    }
}

private fun DeletionError.toUiText(): UiText =
    UiText.Resource(
        when (this) {
            DeletionError.BUSY -> R.string.delete_busy
            DeletionError.UNAVAILABLE -> R.string.delete_unavailable
            DeletionError.UNSAFE_TARGET -> R.string.delete_unsafe
            DeletionError.CHANGED -> R.string.delete_changed
            DeletionError.DELETE_FAILED -> R.string.delete_failed
        }
    )
