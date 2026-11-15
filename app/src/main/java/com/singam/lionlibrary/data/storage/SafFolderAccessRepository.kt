package com.singam.lionlibrary.data.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.singam.lionlibrary.domain.model.FolderGrantPolicy
import com.singam.lionlibrary.domain.model.FolderTree
import com.singam.lionlibrary.domain.repository.FolderAccessRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SafFolderAccessRepository(private val context: Context) : FolderAccessRepository {
    override suspend fun releaseIfUnused(previousUri: String, inUse: List<String>) =
        withContext(Dispatchers.IO) {
            if (previousUri.isBlank()) return@withContext
            val previous = Uri.parse(previousUri)
            // Unparseable identity: keep the grant rather than guess.
            val previousTree = previous.tree() ?: return@withContext
            val inUseTrees = inUse.filter { it.isNotBlank() }.map { Uri.parse(it).tree() }
            if (inUse.any { it.isNotBlank() } && inUseTrees.any { it == null }) return@withContext
            if (!FolderGrantPolicy.canRelease(previousTree, inUseTrees)) return@withContext
            val resolver = context.contentResolver
            val held = resolver.persistedUriPermissions.any { it.uri == previous }
            if (!held) return@withContext
            try {
                resolver.releasePersistableUriPermission(
                    previous,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            } catch (_: SecurityException) {
                // Already gone or never persisted: nothing to release.
            }
        }

    private fun Uri.tree(): FolderTree? =
        try {
            authority?.let { FolderTree(it, DocumentsContract.getTreeDocumentId(this)) }
        } catch (_: IllegalArgumentException) {
            null
        }
}
