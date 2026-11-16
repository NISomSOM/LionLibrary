package com.singam.lionlibrary.data.scanner

import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FolderPermissionException(message: String) : Exception(message)

/**
 * Walks the Movies and Shows roots. Listing is [SafFolderLister]'s job and the folder rules live in
 * [FolderClassifier]; this class only joins them and converts the result. A folder that cannot be
 * listed throws [FolderPermissionException] or [FolderUnavailableException] instead of scanning as
 * empty.
 */
class FolderScanner(private val lister: SafFolderLister, parser: FileNameParser) {
    private val classifier = FolderClassifier(parser, lister)

    // excludedTreeUri: the Shows folder, skipped if it lives inside the Movies folder.
    suspend fun scanMoviesFolder(treeUri: Uri, excludedTreeUri: Uri? = null): List<MediaCandidate> =
        withContext(Dispatchers.IO) {
            val excludeKey = excludedTreeUri?.let { uri ->
                runCatching { "${uri.authority}:${DocumentsContract.getTreeDocumentId(uri)}" }
                    .getOrNull()
            }
            classifier.scanMovies(lister.root(treeUri), excludeKey).map { it.toMediaCandidate() }
        }

    suspend fun scanShowsFolder(treeUri: Uri): List<MediaCandidate> =
        withContext(Dispatchers.IO) {
            classifier.scanShows(lister.root(treeUri)).map { it.toMediaCandidate() }
        }

    private fun ScannedCandidate<Uri>.toMediaCandidate(): MediaCandidate =
        when (this) {
            is ScannedCandidate.Movie -> MediaCandidate.Movie(sourceUri, title, year, subtitleUri)
            is ScannedCandidate.Unknown ->
                MediaCandidate.Unknown(sourceUri, rawName, reason, expectedType)
            is ScannedCandidate.Show ->
                MediaCandidate.Show(
                    title,
                    seasons.mapValues { (_, episodes) ->
                        episodes.map {
                            EpisodeFile(
                                it.uri,
                                it.episodeNumber,
                                it.subtitleUri,
                                it.episodeEndNumber,
                                it.extraTitle,
                            )
                        }
                    },
                )
        }
}
