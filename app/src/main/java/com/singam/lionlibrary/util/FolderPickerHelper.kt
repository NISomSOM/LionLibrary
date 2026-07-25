package com.singam.lionlibrary.util

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri

// Storage Access Framework folder helpers.
object FolderPickerHelper {

    // Creates folder picker intent.
    fun createOpenDocumentTreeIntent(): Intent {
        return Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
        }
    }

    // Requests persistable read access for the folder.
    fun takePersistablePermission(contentResolver: ContentResolver, uri: Uri) {
        contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
    }

    // Formats path to be readable (e.g., "primary:Movies" -> "Movies").
    fun getDisplayPath(uri: Uri): String {
        val path = uri.lastPathSegment ?: return uri.toString()
        // Extract path after colon from SAF tree URIs
        return path.substringAfter(':', path)
    }
}

