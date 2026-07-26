package com.singam.lionlibrary.util

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri

// SAF helpers.
object FolderPickerHelper {

    fun createOpenDocumentTreeIntent(): Intent {
        return Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
        }
    }

    // Persist permission.
    fun takePersistablePermission(contentResolver: ContentResolver, uri: Uri) {
        contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
    }

    // Get display path.
    fun getDisplayPath(uri: Uri): String {
        val path = uri.lastPathSegment ?: return uri.toString()
        return path.substringAfter(':', path)
    }
}
