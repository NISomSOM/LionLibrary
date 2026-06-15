package com.example.mediahub.data.scanner

import android.net.Uri
import com.example.mediahub.domain.model.MediaType

/**
 * Represents a single media file discovered during SAF folder traversal.
 */
data class ScannedFile(
    val uri: Uri,
    val displayName: String,
    val extension: String,
    val mediaType: MediaType,
    val parentFolderName: String
)
