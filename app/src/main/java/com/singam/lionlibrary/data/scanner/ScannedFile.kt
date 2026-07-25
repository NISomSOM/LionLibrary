package com.singam.lionlibrary.data.scanner

import android.net.Uri
import com.singam.lionlibrary.domain.model.MediaType

// Data model for a media file found during a scan.
data class ScannedFile(
    val uri: Uri,
    val displayName: String,
    val extension: String,
    val mediaType: MediaType,
    val parentFolderName: String
)

