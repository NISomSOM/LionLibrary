package com.singam.lionlibrary.data.scanner

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract as DC
import kotlinx.coroutines.CancellationException

/** A library folder could not be listed completely (storage removed, provider error). */
class FolderUnavailableException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

/**
 * Lists a SAF folder with one projected query. Unlike DocumentFile.listFiles(), which swallows
 * provider errors and returns an empty array, every failure here throws, so a revoked or removed
 * folder cannot look like an empty one.
 *
 * Child addresses are built exactly as DocumentFile builds them (buildDocumentUriUsingTree on the
 * parent's address) and no sort order is requested: stored file identities and subtitle tie-breaks
 * depend on both.
 */
class SafFolderLister(context: Context) : FolderLister<Uri> {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver

    /** Same root address DocumentFile.fromTreeUri() resolves. */
    fun root(treeUri: Uri): FolderEntry<Uri> {
        val documentId =
            try {
                if (DC.isDocumentUri(appContext, treeUri)) DC.getDocumentId(treeUri)
                else DC.getTreeDocumentId(treeUri)
            } catch (error: IllegalArgumentException) {
                throw FolderUnavailableException("Not a document tree: $treeUri", error)
            }
        return FolderEntry(
            name = null,
            isDirectory = true,
            isFile = false,
            uri = DC.buildDocumentUriUsingTree(treeUri, documentId),
            documentKey = "${treeUri.authority}:$documentId",
        )
    }

    override fun children(parent: FolderEntry<Uri>): List<FolderEntry<Uri>> {
        val parentUri = parent.uri
        try {
            val childrenUri =
                DC.buildChildDocumentsUriUsingTree(parentUri, DC.getDocumentId(parentUri))
            val cursor =
                resolver.query(childrenUri, columns, null, null, null)
                    ?: throw FolderUnavailableException("No listing for $parentUri")
            return cursor.use {
                if (it.extras?.getBoolean(DC.EXTRA_LOADING, false) == true)
                    throw FolderUnavailableException("Partial listing for $parentUri")
                val idColumn = it.getColumnIndexOrThrow(DC.Document.COLUMN_DOCUMENT_ID)
                val nameColumn = it.getColumnIndexOrThrow(DC.Document.COLUMN_DISPLAY_NAME)
                val mimeColumn = it.getColumnIndexOrThrow(DC.Document.COLUMN_MIME_TYPE)
                val entries = ArrayList<FolderEntry<Uri>>(it.count)
                while (it.moveToNext()) {
                    val documentId =
                        it.getString(idColumn)
                            ?: throw FolderUnavailableException("Row without id in $parentUri")
                    val mime = it.getString(mimeColumn)
                    val directory = mime == DC.Document.MIME_TYPE_DIR
                    entries.add(
                        FolderEntry(
                            name = it.getString(nameColumn),
                            isDirectory = directory,
                            isFile = !directory && !mime.isNullOrEmpty(),
                            uri = DC.buildDocumentUriUsingTree(parentUri, documentId),
                            documentKey = "${parentUri.authority}:$documentId",
                        )
                    )
                }
                entries
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: FolderUnavailableException) {
            throw error
        } catch (error: SecurityException) {
            throw FolderPermissionException("Permission revoked for folder: $parentUri")
        } catch (error: Exception) {
            throw FolderUnavailableException("Could not list $parentUri", error)
        }
    }

    private companion object {
        val columns =
            arrayOf(
                DC.Document.COLUMN_DOCUMENT_ID,
                DC.Document.COLUMN_DISPLAY_NAME,
                DC.Document.COLUMN_MIME_TYPE,
            )
    }
}
