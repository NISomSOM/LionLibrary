package com.singam.lionlibrary.util

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri

// SAF folder picking helpers.
object FolderPickerHelper {

    // Intent for picking a folder.
    fun createOpenDocumentTreeIntent(): Intent {
        return Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
        }
    }

    // Takes a persistable read permission for the folder.
    fun takePersistablePermission(contentResolver: ContentResolver, uri: Uri) {
        contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
    }

    // Returns a readable path (e.g. "primary:Movies" -> "Movies").
    fun getDisplayPath(uri: Uri): String {
        val path = uri.lastPathSegment ?: return uri.toString()
        // SAF tree URIs typically look like "primary:Movies" — extract after the colon
        return path.substringAfter(':', path)
    }
}

