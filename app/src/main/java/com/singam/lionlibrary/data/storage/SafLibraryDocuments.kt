package com.singam.lionlibrary.data.storage

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract as DC
import androidx.core.net.toUri
import com.singam.lionlibrary.domain.model.StorageNode
import com.singam.lionlibrary.domain.model.StorageTree
import com.singam.lionlibrary.util.Constants
import java.io.FileNotFoundException
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class SafLibraryDocuments(context: Context) : LibraryDocuments {
    private class NoDocumentCursor : IOException()

    private val resolver = context.applicationContext.contentResolver

    override fun location(uri: String): DocumentLocation? =
        try {
            val parsed = uri.toUri()
            if (parsed.scheme != "content" || !DC.isTreeUri(parsed)) null
            else
                DocumentLocation(
                    requireNotNull(parsed.authority),
                    DC.getTreeDocumentId(parsed),
                    if ("document" in parsed.pathSegments) DC.getDocumentId(parsed)
                    else DC.getTreeDocumentId(parsed),
                )
        } catch (_: IllegalArgumentException) {
            null
        }

    override suspend fun inventory(rootUri: String): StorageTree =
        withContext(Dispatchers.IO) {
            val root = location(rootUri) ?: throw IOException("Not a document tree")
            val uri = rootUri.toUri()
            val rootNode =
                queryOne(uri, root.treeId, null) ?: throw FileNotFoundException("Root unavailable")
            if (!rootNode.directory) throw IOException("Root is not a directory")
            val nodes = linkedMapOf(rootNode.id to rootNode)
            val pending = ArrayDeque<Pair<String, Int>>().apply { add(rootNode.id to 0) }
            while (pending.isNotEmpty()) {
                currentCoroutineContext().ensureActive()
                val (parent, depth) = pending.removeFirst()
                if (depth > 64) throw IOException("Folder nesting exceeds safe inventory limit")
                val children = DC.buildChildDocumentsUriUsingTree(uri, parent)
                val cursor =
                    resolver.query(children, columns, null, null, null)
                        ?: throw IOException("Provider returned no listing")
                cursor.use {
                    if (it.extras.getBoolean(DC.EXTRA_LOADING, false))
                        throw IOException("Partial listing")
                    while (it.moveToNext()) {
                        val child = node(it, parent)
                        if (nodes.put(child.id, child) != null || nodes.size > 50_000)
                            throw IOException("Repeated document or oversized inventory")
                        if (child.directory) pending.add(child.id to depth + 1)
                    }
                }
            }
            StorageTree(root.authority, root.treeId, nodes)
        }

    override suspend fun presence(rootUri: String, documentId: String): DocumentPresence =
        withContext(Dispatchers.IO) {
            try {
                if (queryOne(rootUri.toUri(), documentId, null) == null) DocumentPresence.MISSING
                else DocumentPresence.PRESENT
            } catch (_: FileNotFoundException) {
                DocumentPresence.MISSING
            } catch (_: NoDocumentCursor) {
                // DocumentsProvider can translate queryDocument's FileNotFoundException into
                // a null cursor. Opening the exact document distinguishes that from a silent
                // query failure. Never infer absence from a null directory listing.
                try {
                    val fd =
                        resolver.openFileDescriptor(
                            DC.buildDocumentUriUsingTree(rootUri.toUri(), documentId),
                            "r",
                        )
                    if (fd == null) DocumentPresence.UNAVAILABLE
                    else {
                        fd.close()
                        DocumentPresence.PRESENT
                    }
                } catch (_: FileNotFoundException) {
                    DocumentPresence.MISSING
                } catch (_: Exception) {
                    DocumentPresence.UNAVAILABLE
                }
            } catch (_: SecurityException) {
                DocumentPresence.UNAVAILABLE
            } catch (_: IOException) {
                DocumentPresence.UNAVAILABLE
            } catch (_: IllegalArgumentException) {
                DocumentPresence.UNAVAILABLE
            }
        }

    override suspend fun delete(rootUri: String, documentId: String): Boolean =
        withContext(Dispatchers.IO) {
            val root = location(rootUri) ?: return@withContext false
            if (documentId == root.treeId) return@withContext false
            DC.deleteDocument(
                resolver,
                DC.buildDocumentUriUsingTree(rootUri.toUri(), documentId),
            )
        }

    private fun queryOne(root: Uri, id: String, parent: String?): StorageNode? {
        val cursor =
            resolver.query(DC.buildDocumentUriUsingTree(root, id), columns, null, null, null)
                ?: throw NoDocumentCursor()
        return cursor.use {
            if (it.extras.getBoolean(DC.EXTRA_LOADING, false)) throw IOException("Partial document")
            if (!it.moveToFirst()) null
            else
                node(it, parent).also { node ->
                    if (node.id != id || it.moveToNext())
                        throw IOException("Ambiguous document identity")
                }
        }
    }

    private fun node(cursor: Cursor, parent: String?): StorageNode {
        fun value(column: String) =
            cursor.getString(cursor.getColumnIndexOrThrow(column))
                ?: throw IOException("Missing document metadata")
        fun number(column: String): Long {
            val index = cursor.getColumnIndexOrThrow(column)
            return if (cursor.isNull(index)) -1 else cursor.getLong(index)
        }
        val name = value(DC.Document.COLUMN_DISPLAY_NAME)
        val mime = value(DC.Document.COLUMN_MIME_TYPE)
        val directory = mime == DC.Document.MIME_TYPE_DIR
        val flags = number(DC.Document.COLUMN_FLAGS)
        if (flags < 0) throw IOException("Missing provider flags")
        return StorageNode(
            value(DC.Document.COLUMN_DOCUMENT_ID),
            parent,
            name,
            directory,
            flags and DC.Document.FLAG_SUPPORTS_DELETE.toLong() != 0L,
            number(DC.Document.COLUMN_SIZE),
            number(DC.Document.COLUMN_LAST_MODIFIED),
            !directory &&
                (mime.startsWith("video/") ||
                    name.substringAfterLast('.', "").lowercase() in
                        (Constants.SUPPORTED_VIDEO_EXTENSIONS +
                            setOf("ts", "m2ts", "mts", "wmv", "vob", "ogm", "divx", "iso"))),
        )
    }

    private companion object {
        val columns =
            arrayOf(
                DC.Document.COLUMN_DOCUMENT_ID,
                DC.Document.COLUMN_DISPLAY_NAME,
                DC.Document.COLUMN_MIME_TYPE,
                DC.Document.COLUMN_FLAGS,
                DC.Document.COLUMN_SIZE,
                DC.Document.COLUMN_LAST_MODIFIED,
            )
    }
}
