package com.singam.lionlibrary

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract as DC
import android.provider.DocumentsProvider
import java.io.File
import java.io.FileNotFoundException
import java.util.UUID

/** Debug integration fixtures only, confined to a dedicated private-cache directory. */
class DeletionFixtureProvider : DocumentsProvider() {
    private val files = java.util.concurrent.ConcurrentHashMap<String, File>()
    private val base
        get() = File(requireNotNull(context).cacheDir, "deletion-documents").canonicalFile

    override fun onCreate() = true

    private fun register(file: File): String {
        val canonical = file.canonicalFile
        if (!within(canonical, base)) throw SecurityException("Outside fixtures")
        return ("opaque-" + UUID.nameUUIDFromBytes(canonical.path.toByteArray())).also {
            files[it] = canonical
        }
    }

    private fun resolve(id: String): File {
        listOf("Movies", "Shows").forEach { register(File(base, it)) }
        return files[id]?.takeIf {
            within(it, base) && it.exists()
        } ?: throw FileNotFoundException("Missing fixture")
    }

    override fun queryRoots(projection: Array<out String>?): Cursor =
        MatrixCursor(projection ?: arrayOf(DC.Root.COLUMN_ROOT_ID))

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor =
        MatrixCursor(projection ?: columns).apply { addFile(this, resolve(documentId)) }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?,
    ): Cursor =
        MatrixCursor(projection ?: columns).apply {
            if (loading) {
                extras = android.os.Bundle().apply { putBoolean(DC.EXTRA_LOADING, true) }
                return@apply
            }
            val children =
                resolve(parentDocumentId).listFiles()
                    ?: throw FileNotFoundException("No fixture directory")
            children.forEach { addFile(this, it) }
        }

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?,
    ): ParcelFileDescriptor =
        ParcelFileDescriptor.open(resolve(documentId), ParcelFileDescriptor.parseMode(mode))

    override fun deleteDocument(documentId: String) {
        val file = resolve(documentId)
        if (file == base || file.parentFile == base)
            throw SecurityException("Fixture root protected")
        if (!file.deleteRecursively()) throw java.io.IOException("Fixture deletion failed")
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean {
        val parent = resolve(parentDocumentId).canonicalFile
        val child = resolve(documentId).canonicalFile
        return child != parent && within(child, parent)
    }

    private fun within(file: File, parent: File): Boolean {
        var current: File? = file.canonicalFile
        while (current != null) {
            if (current == parent.canonicalFile) return true
            current = current.parentFile
        }
        return false
    }

    private fun addFile(cursor: MatrixCursor, file: File) {
        val id = register(file)
        val values =
            mapOf<String, Any>(
                DC.Document.COLUMN_DOCUMENT_ID to id,
                DC.Document.COLUMN_DISPLAY_NAME to file.name,
                DC.Document.COLUMN_MIME_TYPE to
                    if (file.isDirectory) DC.Document.MIME_TYPE_DIR else "video/mp4",
                DC.Document.COLUMN_FLAGS to DC.Document.FLAG_SUPPORTS_DELETE,
                DC.Document.COLUMN_SIZE to file.length(),
                DC.Document.COLUMN_LAST_MODIFIED to file.lastModified(),
            )
        cursor.addRow(cursor.columnNames.map { values[it] }.toTypedArray())
    }

    companion object {
        @Volatile var loading = false
        val columns =
            arrayOf(
                DC.Document.COLUMN_DOCUMENT_ID,
                DC.Document.COLUMN_DISPLAY_NAME,
                DC.Document.COLUMN_MIME_TYPE,
                DC.Document.COLUMN_FLAGS,
                DC.Document.COLUMN_SIZE,
                DC.Document.COLUMN_LAST_MODIFIED,
            )

        fun rootId(context: android.content.Context, name: String): String {
            val file = File(context.cacheDir, "deletion-documents/$name").canonicalFile
            // Provider lookup starts from the root document query.
            val id = "opaque-" + UUID.nameUUIDFromBytes(file.path.toByteArray())
            return id
        }
    }
}
