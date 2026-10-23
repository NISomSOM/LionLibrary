package com.singam.lionlibrary.presentation

import assertk.assertThat
import assertk.assertions.*
import com.singam.lionlibrary.domain.model.OperationResult
import com.singam.lionlibrary.domain.repository.*
import com.singam.lionlibrary.presentation.common.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeletionPresenterTest {
    // No IO/clock. Attack preview cancellation, double-confirm, absent authorization and error
    // reset.
    @Test
    fun `request only previews and dismissal never deletes`() =
        runTest(UnconfinedTestDispatcher()) {
            val repo = DeletionFake()
            val presenter = DeletionPresenter(repo, backgroundScope, {}, {})
            presenter.request(DeletionTarget(1))
            assertThat(presenter.state.value.preview).isEqualTo(repo.preview)
            presenter.dismiss()
            presenter.confirm()
            assertThat(repo.deletes).isEqualTo(0)
        }

    @Test
    fun `duplicate confirms execute exactly one reviewed token`() =
        runTest(UnconfinedTestDispatcher()) {
            val repo = DeletionFake()
            val outcomes = mutableListOf<DeletionOutcome>()
            val presenter = DeletionPresenter(repo, backgroundScope, { outcomes += it }, {})
            presenter.request(DeletionTarget(1))
            presenter.confirm()
            presenter.confirm()
            presenter.dismiss()
            assertThat(repo.deletes).isEqualTo(1)
            assertThat(presenter.state.value.deleting).isTrue()
            repo.finish.complete(OperationResult.Success(DeletionOutcome(setOf(1))))
            runCurrent()
            assertThat(outcomes).hasSize(1)
            assertThat(presenter.state.value.preview).isNull()
        }

    @Test
    fun `cancelling slow preview cannot publish a late confirmation`() =
        runTest(UnconfinedTestDispatcher()) {
            val repo = DeletionFake().apply { delayPreview = true }
            val presenter = DeletionPresenter(repo, backgroundScope, {}, {})
            presenter.request(DeletionTarget(1))
            presenter.dismiss()
            repo.allowPreview.complete(Unit)
            runCurrent()
            assertThat(presenter.state.value.preview).isNull()
            assertThat(repo.deletes).isEqualTo(0)
        }

    @Test
    fun `provider refusal clears busy state and reports without retry`() =
        runTest(UnconfinedTestDispatcher()) {
            val repo = DeletionFake()
            val messages = mutableListOf<UiText>()
            val presenter = DeletionPresenter(repo, backgroundScope, {}, { messages += it })
            presenter.request(DeletionTarget(1))
            presenter.confirm()
            repo.finish.complete(OperationResult.Failure(DeletionError.DELETE_FAILED))
            runCurrent()
            assertThat(presenter.state.value.deleting).isFalse()
            assertThat(messages).hasSize(1)
            assertThat(repo.deletes).isEqualTo(1)
        }
}

private class DeletionFake : MediaDeletionRepository {
    val preview = DeletionPreview("token", "映画 🦁", "Movies/映画.mkv", false, 1)
    val finish = CompletableDeferred<OperationResult<DeletionOutcome, DeletionError>>()
    val allowPreview = CompletableDeferred<Unit>()
    var delayPreview = false
    var deletes = 0

    override suspend fun preview(
        target: DeletionTarget
    ): OperationResult<DeletionPreview, DeletionError> {
        if (delayPreview) allowPreview.await()
        return OperationResult.Success(preview)
    }

    override suspend fun delete(
        preview: DeletionPreview
    ): OperationResult<DeletionOutcome, DeletionError> {
        check(preview == this.preview)
        deletes++
        return finish.await()
    }
}
