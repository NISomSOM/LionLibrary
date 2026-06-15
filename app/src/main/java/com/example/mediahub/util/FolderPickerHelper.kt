package com.example.mediahub.util

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri

/**
 * Utility helpers for SAF (Storage Access Framework) folder picking.
 */
object FolderPickerHelper {

    /**
     * Creates an intent to launch the SAF document tree picker.
     */
    fun createOpenDocumentTreeIntent(): Intent {
        return Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
        }
    }

    /**
     * Takes a persistable read permission for the given SAF tree URI.
     * Call this in the ActivityResult callback after the user picks a folder.
     */
    fun takePersistablePermission(contentResolver: ContentResolver, uri: Uri) {
        contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
    }

    /**
     * Returns a human-readable display path from a SAF URI.
     * Extracts the last path segment or returns the full URI string as fallback.
     */
    fun getDisplayPath(uri: Uri): String {
        val path = uri.lastPathSegment ?: return uri.toString()
        // SAF tree URIs typically look like "primary:Movies" — extract after the colon
        return path.substringAfter(':', path)
    }
}
