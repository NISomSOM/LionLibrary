package com.singam.lionlibrary.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.singam.lionlibrary.data.local.db.MediaDatabase
import com.singam.lionlibrary.data.local.db.entity.*
import com.singam.lionlibrary.data.repository.*
import com.singam.lionlibrary.data.storage.*
import com.singam.lionlibrary.domain.model.*
import com.singam.lionlibrary.domain.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test

/** Integration fixtures only. Never use the customer's database/storage for this suite. */
class DeletionIntegrationTest {
    @Test
    fun rescanReconcilesMissingFilesBeforeMetadataKeyValidation() = scenario {
        movie(1, "one")
        movie(2, "two")
        docs.remove("M", "one")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val parser = com.singam.lionlibrary.data.scanner.FileNameParser()
        val api =
            java.lang.reflect.Proxy.newProxyInstance(
                com.singam.lionlibrary.data.remote.api.TmdbApiService::class.java.classLoader,
                arrayOf(com.singam.lionlibrary.data.remote.api.TmdbApiService::class.java),
            ) { _, _, _ ->
                error("No metadata network request is allowed in this fixture")
            } as com.singam.lionlibrary.data.remote.api.TmdbApiService
        val scanner =
            com.singam.lionlibrary.data.scanner.AndroidMediaScanner(
                context,
                com.singam.lionlibrary.data.scanner.FolderScanner(context, parser),
                parser,
                api,
                com.singam.lionlibrary.data.scanner.ImageCacheManager(
                    context,
                    okhttp3.OkHttpClient(),
                ),
                db.mediaDao(),
                db.seasonDao(),
                db.episodeDao(),
                settings,
                db,
                reconciler,
                gate,
            )
        assertEquals(ScanStatus.API_KEY_MISSING, scanner().toList().last().status)
        assertNull(db.mediaDao().getById(1))
        assertNotNull(db.mediaDao().getById(2))
    }

    @Test
    fun replacingSameFileDuringConfirmationAndTamperedPreviewNeverDeletes() = scenario {
        movie(1, "one")
        val plan = preview(DeletionTarget(1))
        docs.put("M", docs.trees.getValue("M").nodes.getValue("one").copy(size = 99, modified = 99))
        assertEquals(OperationResult.Failure(DeletionError.CHANGED), repository.delete(plan))
        val second = preview(DeletionTarget(1))
        assertEquals(
            OperationResult.Failure(DeletionError.CHANGED),
            repository.delete(second.copy(location = "Forged path")),
        )
        assertTrue(docs.calls.isEmpty())
    }

    @Test
    fun nestedOtherLibraryRootBlocksShowFolderDeletion() = scenario {
        show()
        docs.put("S", node("nested", "show"))
        settings.movies.value = "fixture|nested|nested"
        assertEquals(
            OperationResult.Failure(DeletionError.UNSAFE_TARGET),
            repository.preview(DeletionTarget(1)),
        )
        assertTrue(docs.calls.isEmpty())
    }

    @Test
    fun rowsFromUnconfiguredRootRemainEvenWhenTheirDocumentsAreMissing() = scenario {
        movie(1, "one")
        docs.remove("M", "one")
        settings.movies.value = ""
        reconciler.reconcile()
        assertNotNull(db.mediaDao().getById(1))
        assertNotNull(db.watchProgressDao().getProgress(1, 0))
    }

    private fun scenario(test: suspend Fixture.() -> Unit) = runBlocking {
        val db =
            Room.inMemoryDatabaseBuilder(
                    InstrumentationRegistry.getInstrumentation().targetContext,
                    MediaDatabase::class.java,
                )
                .build()
        try {
            Fixture(db).test()
        } finally {
            db.close()
        }
    }

    @Test
    fun externalDeletionRemovesOnlyMissingMovieAndItsHistoryEvenWhenRootBecomesEmpty() = scenario {
        movie(1, "one")
        settings.shows.value = ""
        docs.remove("M", "one")
        reconciler.reconcile()
        assertNull(db.mediaDao().getById(1))
        assertNull(db.watchProgressDao().getProgress(1, 0))
    }

    @Test
    fun failedAndPartialInventoryNeverPrunesRows() = scenario {
        movie(1, "one")
        movie(2, "two")
        docs.failInventory = true
        reconciler.reconcile()
        assertEquals(2, db.mediaDao().getAll().first().size)
        docs.failInventory = false
        docs.omitFromInventory = "one" // Direct provider query still proves the file exists.
        reconciler.reconcile()
        assertNotNull(db.mediaDao().getById(1))
        assertNotNull(db.watchProgressDao().getProgress(1, 0))
    }

    @Test
    fun missingEpisodePrunesEmptySeasonAndEventuallyShowWithoutTouchingSiblingHistory() = scenario {
        show()
        movie(9, "sibling")
        docs.remove("S", "ep1")
        reconciler.reconcile()
        assertNull(db.episodeDao().getById(11))
        assertNotNull(db.episodeDao().getById(12))
        assertNotNull(db.watchProgressDao().getProgress(1, 12))
        docs.remove("S", "ep2")
        reconciler.reconcile()
        assertNull(db.mediaDao().getById(1))
        assertNotNull(db.mediaDao().getById(9))
        assertNotNull(db.watchProgressDao().getProgress(9, 0))
    }

    @Test
    fun movieDeletesOnlyReviewedFileNeverItsFolderOrSibling() = scenario {
        movie(1, "one")
        movie(2, "two")
        val plan = preview(DeletionTarget(1))
        assertFalse(plan.isFolder)
        assertTrue(repository.delete(plan) is OperationResult.Success)
        assertEquals(listOf("M" to "one"), docs.calls)
        assertNotNull(docs.trees.getValue("M").nodes["two"])
        assertNotNull(db.mediaDao().getById(2))
        assertNotNull(db.watchProgressDao().getProgress(2, 0))
        assertTrue(repository.delete(plan) is OperationResult.Failure)
        assertEquals(1, docs.calls.size)
    }

    @Test
    fun showDeletesOneOwnedFolderIncludingSubtitlesButNeverLibraryRoot() = scenario {
        show()
        docs.put("S", node("subtitle", "show", false, false))
        val plan = preview(DeletionTarget(1))
        assertTrue(plan.isFolder)
        assertEquals(3, plan.fileCount)
        assertTrue(repository.delete(plan) is OperationResult.Success)
        assertEquals(listOf("S" to "show"), docs.calls)
        assertNotNull(docs.trees.getValue("S").nodes["S"])
        assertNull(db.mediaDao().getById(1))
    }

    @Test
    fun episodeDeletesOnlyCombinedFileAndKeepsOtherEpisode() = scenario {
        show()
        val ep = db.episodeDao().getById(11)!!
        db.episodeDao().insert(ep.copy(episodeEndNumber = 3))
        val plan = preview(DeletionTarget(1, 11))
        assertTrue(plan.combinedEpisode)
        assertFalse(plan.isFolder)
        assertTrue(repository.delete(plan) is OperationResult.Success)
        assertEquals(listOf("S" to "ep1"), docs.calls)
        assertNull(db.episodeDao().getById(11))
        assertNotNull(db.episodeDao().getById(12))
    }

    @Test
    fun changedContentsAndChangedRootInvalidateConfirmationWithoutNativeDelete() = scenario {
        show()
        val plan = preview(DeletionTarget(1))
        docs.put("S", node("new", "show", false, true))
        assertTrue(repository.delete(plan) is OperationResult.Failure)
        assertTrue(docs.calls.isEmpty())
        docs.remove("S", "new")
        val second = preview(DeletionTarget(1))
        settings.shows.value = "fixture|elsewhere|elsewhere"
        assertTrue(repository.delete(second) is OperationResult.Failure)
        assertTrue(docs.calls.isEmpty())
    }

    @Test
    fun sharedFolderLooseRootAndWrongEpisodeOwnerAreRefused() = scenario {
        show()
        movie(9, "sibling")
        docs.put("S", node("unindexed", "show", false, true))
        assertTrue(repository.preview(DeletionTarget(1)) is OperationResult.Failure)
        docs.remove("S", "unindexed")
        assertEquals(
            OperationResult.Failure(DeletionError.UNSAFE_TARGET),
            repository.preview(DeletionTarget(9, 11)),
        )
        docs.put("S", docs.trees.getValue("S").nodes.getValue("ep1").copy(parentId = "S"))
        assertTrue(repository.preview(DeletionTarget(1)) is OperationResult.Failure)
        assertTrue(docs.calls.isEmpty())
    }

    @Test
    fun activeScanGateAndReadOnlyFileCannotReachNativeDelete() = scenario {
        movie(1, "one")
        gate.mutex.lock()
        assertEquals(
            OperationResult.Failure(DeletionError.BUSY),
            repository.preview(DeletionTarget(1)),
        )
        gate.mutex.unlock()
        docs.put("M", docs.trees.getValue("M").nodes.getValue("one").copy(deletable = false))
        assertTrue(repository.preview(DeletionTarget(1)) is OperationResult.Failure)
        assertTrue(docs.calls.isEmpty())
    }

    @Test
    fun partialNativeFailureReconcilesOnlyActualMissingFilesWithNoParentRetry() = scenario {
        show()
        val plan = preview(DeletionTarget(1))
        docs.partialDelete = "ep1"
        assertEquals(OperationResult.Failure(DeletionError.DELETE_FAILED), repository.delete(plan))
        assertEquals(listOf("S" to "show"), docs.calls)
        assertNull(db.episodeDao().getById(11))
        assertNotNull(db.episodeDao().getById(12))
        assertNotNull(db.mediaDao().getById(1))
    }

    @Test
    fun cancellationAfterMutationStartsStillFinishesCleanupAndLatePlaybackCannotRecreateHistory() =
        scenario {
            movie(1, "one")
            val plan = preview(DeletionTarget(1))
            docs.waitBeforeDelete = true
            val job = CoroutineScope(Dispatchers.Default).launch { repository.delete(plan) }
            docs.started.await()
            job.cancel()
            docs.allowDelete.complete(Unit)
            job.join()
            assertNull(db.mediaDao().getById(1))
            val playback =
                RoomPlaybackRepository(db.mediaDao(), db.episodeDao(), db.watchProgressDao(), db)
            playback.save(PlaybackSnapshot(1, 0, 5, 10, false, 123)).await()
            assertNull(db.watchProgressDao().getProgress(1, 0))
            assertTrue(gate.mutex.tryLock())
            gate.mutex.unlock()
        }
}

private class Fixture(val db: MediaDatabase) {
    val settings = DeletionSettings()
    val docs = MemoryDocuments()
    val gate = LibraryMutationGate()
    val reconciler = LibraryReconciler(db, settings, docs)
    val repository = SafMediaDeletionRepository(db, settings, docs, reconciler, gate)

    suspend fun preview(target: DeletionTarget) =
        (repository.preview(target) as OperationResult.Success).data

    suspend fun movie(id: Long, file: String) {
        docs.put("M", node(file, "M", false, true))
        db.mediaDao().insert(media(id, "MOVIE", "fixture|M|$file"))
        db.watchProgressDao().upsert(WatchProgressEntity(id, 0, .5f, 5, 10, 1, false))
    }

    suspend fun show() {
        docs.put("S", node("show", "S"))
        db.mediaDao().insert(media(1, "ANIME", null))
        db.seasonDao().insert(SeasonEntity(1, 1, 1, "Season 1", null))
        for (n in 1..2) {
            docs.put("S", node("ep$n", "show", false, true))
            db.episodeDao()
                .insert(
                    EpisodeEntity(10L + n, 1, 1, n, null, null, null, null, null, "fixture|S|ep$n")
                )
            db.watchProgressDao().upsert(WatchProgressEntity(1, 10L + n, .5f, 5, 10, 1, false))
        }
    }

    private fun media(id: Long, type: String, path: String?) =
        MediaEntity(
            id,
            id.toInt(),
            "🦁 $id",
            null,
            null,
            null,
            null,
            genres = null,
            rating = null,
            year = null,
            mediaType = type,
            matchConfidence = 1f,
            isUnidentified = false,
            lastUpdated = 1,
            filePath = path,
        )
}

private fun node(id: String, parent: String?, directory: Boolean = true, video: Boolean = false) =
    StorageNode(id, parent, id, directory, true, 1, 1, video)

private class MemoryDocuments : LibraryDocuments {
    val trees =
        mutableMapOf(
            "M" to StorageTree("fixture", "M", mapOf("M" to node("M", null))),
            "S" to StorageTree("fixture", "S", mapOf("S" to node("S", null))),
        )
    var failInventory = false
    var omitFromInventory: String? = null
    var partialDelete: String? = null
    var waitBeforeDelete = false
    val started = CompletableDeferred<Unit>()
    val allowDelete = CompletableDeferred<Unit>()
    val calls = mutableListOf<Pair<String, String>>()

    override fun location(uri: String): DocumentLocation? =
        uri.split('|').takeIf { it.size == 3 }?.let { DocumentLocation(it[0], it[1], it[2]) }

    override suspend fun inventory(rootUri: String): StorageTree {
        if (failInventory) throw java.io.IOException("Offline or partial provider")
        val tree = trees.getValue(location(rootUri)!!.treeId)
        return tree.copy(nodes = omitFromInventory?.let { tree.nodes - it } ?: tree.nodes)
    }

    override suspend fun presence(rootUri: String, documentId: String) =
        if (documentId in trees.getValue(location(rootUri)!!.treeId).nodes) DocumentPresence.PRESENT
        else DocumentPresence.MISSING

    override suspend fun delete(rootUri: String, documentId: String): Boolean {
        val root = location(rootUri)!!.treeId
        calls += root to documentId
        started.complete(Unit)
        if (waitBeforeDelete) allowDelete.await()
        partialDelete?.let {
            remove(root, it)
            return false
        }
        val tree = trees.getValue(root)
        trees[root] = tree.copy(nodes = tree.nodes - tree.subtree(documentId))
        return true
    }

    fun put(root: String, node: StorageNode) {
        trees[root] = trees.getValue(root).let { it.copy(nodes = it.nodes + (node.id to node)) }
    }

    fun remove(root: String, id: String) {
        trees[root] = trees.getValue(root).let { it.copy(nodes = it.nodes - id) }
    }
}

private class DeletionSettings : SettingsRepository {
    val movies = MutableStateFlow("fixture|M|M")
    val shows = MutableStateFlow("fixture|S|S")
    override val moviesFolderUri = movies
    override val showsFolderUri = shows
    override val tmdbApiKey = flowOf("")
    override val lastScanTime = flowOf(0L)
    override val forceLibVlc = flowOf(false)
    override val forceExternalPlayer = flowOf(false)
    override val onlineSkipTimes = flowOf(false)
    override val keyVerified = flowOf(false)
    override val setupDismissed = flowOf(false)
    override val scanSummary = flowOf(ScanSummary())

    override suspend fun setMoviesFolderUri(uri: String) {
        movies.value = uri
    }

    override suspend fun setShowsFolderUri(uri: String) {
        shows.value = uri
    }

    override suspend fun setTmdbApiKey(key: String) {}

    override suspend fun setLastScanTime(time: Long) {}

    override suspend fun setForceLibVlc(enabled: Boolean) {}

    override suspend fun setForceExternalPlayer(enabled: Boolean) {}

    override suspend fun setOnlineSkipTimes(enabled: Boolean) {}

    override suspend fun setKeyVerified(verified: Boolean) {}

    override suspend fun setSetupDismissed(dismissed: Boolean) {}

    override suspend fun setScanSummary(summary: ScanSummary) {}
}
