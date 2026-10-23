package com.singam.lionlibrary.data.storage

import androidx.room.withTransaction
import com.singam.lionlibrary.data.local.db.MediaDatabase
import com.singam.lionlibrary.domain.model.StorageTree
import com.singam.lionlibrary.domain.repository.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first

/**
 * Metadata cleanup only: this class never deletes storage documents or artwork. Caller holds gate.
 */
class LibraryReconciler(
    private val database: MediaDatabase,
    private val settings: SettingsRepository,
    private val documents: LibraryDocuments,
) {
    suspend fun roots(): List<String> =
        listOf(settings.moviesFolderUri.first(), settings.showsFolderUri.first())
            .filter { it.isNotBlank() }
            .distinct()

    suspend fun reconcile(): Set<Long> {
        val removed = mutableSetOf<Long>()
        val configured = roots()
        for (root in configured) {
            val tree =
                try {
                    documents.inventory(root)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    continue
                } // Offline/revoked/partial root must retain its rows.
            if (roots() != configured) return removed
            removed += reconcileTree(root, tree, configured)
        }
        return removed
    }

    /**
     * Folds unidentified rows that duplicate an existing episode file back into that episode,
     * merging history with [mergeDuplicateProgress] and keeping watchlist membership. Touches no
     * storage. Repeatable: once a duplicate is removed there is nothing left to merge.
     */
    suspend fun mergeEpisodeDuplicates(): Int = database.withTransaction {
        val media = database.mediaDao()
        val episodes = database.episodeDao()
        val history = database.watchProgressDao()
        var merged = 0
        for (duplicate in media.getUnidentifiedEpisodeDuplicates()) {
            val path = duplicate.filePath ?: continue
            val episode = episodes.getByFilePath(path) ?: continue
            history.getProgress(duplicate.id, 0L)?.let { progress ->
                history.upsert(
                    mergeDuplicateProgress(
                        history.getProgress(episode.showId, episode.id),
                        progress,
                        episode.showId,
                        episode.id,
                    )
                )
            }
            if (duplicate.isWatchlisted) media.addToWatchlist(episode.showId)
            history.deleteProgressForMedia(duplicate.id)
            media.deleteById(duplicate.id)
            merged++
        }
        merged
    }

    private suspend fun reconcileTree(
        root: String,
        tree: StorageTree,
        configured: List<String>,
    ): Set<Long> {
        val media = database.mediaDao().getAll().first()
        val episodes = database.episodeDao().getAll()
        val showsWithEpisodes = episodes.mapTo(hashSetOf()) { it.showId }
        suspend fun absent(path: String?): Boolean {
            val key = path?.let(documents::location) ?: return false
            if (
                key.authority != tree.authority || key.treeId != tree.rootId || key.id in tree.nodes
            )
                return false
            return documents.presence(root, key.id) == DocumentPresence.MISSING
        }
        val missingMedia = media.filter { item ->
            item.id !in showsWithEpisodes && absent(item.filePath)
        }
        val missingEpisodes = episodes.filter { absent(it.filePath) }
        if (roots() != configured) return emptySet()
        currentCoroutineContext().ensureActive()
        return database.withTransaction {
            val removed = mutableSetOf<Long>()
            for (item in missingMedia) {
                // Identity can have been repaired while inventory was running.
                if (database.mediaDao().getById(item.id)?.filePath != item.filePath) continue
                database.watchProgressDao().deleteProgressForMedia(item.id)
                database.mediaDao().deleteById(item.id)
                removed += item.id
            }
            for (episode in missingEpisodes) {
                if (database.episodeDao().getById(episode.id)?.filePath != episode.filePath)
                    continue
                database.watchProgressDao().deleteProgress(episode.showId, episode.id)
                database.episodeDao().deleteById(episode.id)
            }
            for (show in missingEpisodes.map { it.showId }.distinct()) {
                database.seasonDao().deleteEmptyForShow(show)
                if (database.episodeDao().countForShow(show) == 0) {
                    database.watchProgressDao().deleteProgressForMedia(show)
                    database.mediaDao().deleteById(show)
                    removed += show
                }
            }
            removed
        }
    }
}
