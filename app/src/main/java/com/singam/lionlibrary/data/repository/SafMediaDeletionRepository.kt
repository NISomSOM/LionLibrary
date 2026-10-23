package com.singam.lionlibrary.data.repository

import com.singam.lionlibrary.data.local.db.MediaDatabase
import com.singam.lionlibrary.data.storage.*
import com.singam.lionlibrary.domain.model.*
import com.singam.lionlibrary.domain.repository.*
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class SafMediaDeletionRepository(
    private val database: MediaDatabase,
    private val settings: SettingsRepository,
    private val documents: LibraryDocuments,
    private val reconciler: LibraryReconciler,
    private val gate: LibraryMutationGate,
) : MediaDeletionRepository {
    private data class Prepared(
        val request: DeletionTarget,
        val roots: List<String>,
        val root: String,
        val document: String,
        val contents: Set<StorageNode>,
        val preview: DeletionPreview,
    )

    private val pending = linkedMapOf<String, Prepared>()

    private class Refused(val error: DeletionError) : Exception()

    override suspend fun preview(
        target: DeletionTarget
    ): OperationResult<DeletionPreview, DeletionError> =
        withContext(Dispatchers.IO) {
            if (!gate.mutex.tryLock())
                return@withContext OperationResult.Failure(DeletionError.BUSY)
            try {
                val plan = prepare(target, UUID.randomUUID().toString())
                if (pending.size >= 8) pending.remove(pending.keys.first())
                pending[plan.preview.token] = plan
                OperationResult.Success(plan.preview)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (refused: Refused) {
                OperationResult.Failure(refused.error)
            } catch (_: Exception) {
                OperationResult.Failure(DeletionError.UNAVAILABLE)
            } finally {
                gate.mutex.unlock()
            }
        }

    override suspend fun delete(
        preview: DeletionPreview
    ): OperationResult<DeletionOutcome, DeletionError> =
        withContext(Dispatchers.IO) {
            if (!gate.mutex.tryLock())
                return@withContext OperationResult.Failure(DeletionError.BUSY)
            try {
                val approved = pending.remove(preview.token) ?: throw Refused(DeletionError.CHANGED)
                if (approved.preview != preview) throw Refused(DeletionError.CHANGED)
                val current =
                    try {
                        prepare(approved.request, preview.token)
                    } catch (_: Refused) {
                        throw Refused(DeletionError.CHANGED)
                    }
                if (current != approved) throw Refused(DeletionError.CHANGED)
                currentCoroutineContext().ensureActive()
                // Once storage mutation begins, finish metadata cleanup even if the screen closes.
                withContext(NonCancellable) {
                    var deleted = false
                    try {
                        deleted = documents.delete(approved.root, approved.document)
                    } catch (_: Exception) {
                        /* No fallback path/parent deletion. Inspect actual state. */
                    }
                    val removed = reconciler.reconcile()
                    if (
                        deleted &&
                            documents.presence(approved.root, approved.document) ==
                                DocumentPresence.MISSING
                    )
                        OperationResult.Success(DeletionOutcome(removed))
                    else OperationResult.Failure(DeletionError.DELETE_FAILED)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (refused: Refused) {
                OperationResult.Failure(refused.error)
            } catch (_: Exception) {
                OperationResult.Failure(DeletionError.UNAVAILABLE)
            } finally {
                gate.mutex.unlock()
            }
        }

    private suspend fun prepare(request: DeletionTarget, token: String): Prepared {
        val roots = reconciler.roots()
        val media =
            database.mediaDao().getById(request.mediaId) ?: throw Refused(DeletionError.UNAVAILABLE)
        val allEpisodes = database.episodeDao().getAll()
        val allMedia = database.mediaDao().getAll().first()
        val episode =
            request.episodeId?.let { id ->
                allEpisodes.find { it.id == id && it.showId == media.id }
                    ?: throw Refused(DeletionError.UNSAFE_TARGET)
            }
        val show =
            episode == null && media.filePath == null && media.mediaType != MediaType.MOVIE.name
        val ownedPaths =
            if (show) allEpisodes.filter { it.showId == media.id }.map { it.filePath }
            else
                listOf(
                    episode?.filePath ?: media.filePath ?: throw Refused(DeletionError.UNAVAILABLE)
                )
        if (ownedPaths.isEmpty()) throw Refused(DeletionError.UNSAFE_TARGET)
        val locations = ownedPaths.map {
            documents.location(it) ?: throw Refused(DeletionError.UNSAFE_TARGET)
        }
        val scope = locations.first()
        if (locations.any { it.authority != scope.authority || it.treeId != scope.treeId })
            throw Refused(DeletionError.UNSAFE_TARGET)
        val root =
            roots.singleOrNull { uri ->
                documents.location(uri)?.let {
                    it.authority == scope.authority && it.treeId == scope.treeId
                } == true
            } ?: throw Refused(DeletionError.UNSAFE_TARGET)
        if (show && root != settings.showsFolderUri.first())
            throw Refused(DeletionError.UNSAFE_TARGET)
        val tree = documents.inventory(root)
        val ownIds = locations.map { it.id }.toSet()
        val otherPaths =
            allMedia.filter { it.id != media.id }.mapNotNull { it.filePath } +
                allEpisodes
                    .filter { if (show) it.showId != media.id else it.id != episode?.id }
                    .map { it.filePath }
        val otherIds =
            otherPaths
                .mapNotNull { documents.location(it) }
                .filter {
                    it.authority == tree.authority
                }
                .map { it.id }
                .toSet()
        val target =
            if (show)
                DeletionSafety.showFolder(tree, ownIds, otherIds)
                    ?: throw Refused(DeletionError.UNSAFE_TARGET)
            else
                tree.nodes[ownIds.single()]?.takeIf {
                    !it.directory &&
                        it.video &&
                        it.deletable &&
                        it.id != tree.rootId &&
                        it.id !in otherIds
                } ?: throw Refused(DeletionError.UNSAFE_TARGET)
        val ids = tree.subtree(target.id)
        if (
            roots.mapNotNull(documents::location).any {
                it.authority == tree.authority && it.treeId in ids
            }
        )
            throw Refused(DeletionError.UNSAFE_TARGET)
        val nodes = ids.map { tree.nodes.getValue(it) }.toSet()
        // Incomplete metadata cannot reliably detect replacing a file during confirmation.
        if (nodes.any { !it.directory && (it.size < 0 || it.modified < 0) })
            throw Refused(DeletionError.UNSAFE_TARGET)
        val path = mutableListOf(target.name)
        var ancestor = target.parentId
        while (ancestor != null) {
            val parent = tree.nodes[ancestor] ?: throw Refused(DeletionError.UNSAFE_TARGET)
            path.add(0, parent.name)
            ancestor = parent.parentId
        }
        val title =
            if (episode == null) media.title
            else
                "${media.title} · S${episode.seasonNumber} E${episode.episodeNumber}" +
                    (episode.episodeEndNumber?.let { "–E$it" } ?: "")
        if (reconciler.roots() != roots) throw Refused(DeletionError.CHANGED)
        return Prepared(
            request,
            roots,
            root,
            target.id,
            nodes,
            DeletionPreview(
                token,
                title,
                path.joinToString("/"),
                target.directory,
                nodes.count { !it.directory },
                combinedEpisode =
                    episode?.let { (it.episodeEndNumber ?: it.episodeNumber) > it.episodeNumber } ==
                        true,
            ),
        )
    }
}
