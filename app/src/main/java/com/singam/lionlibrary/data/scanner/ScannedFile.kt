package com.singam.lionlibrary.data.scanner

import android.net.Uri
import com.singam.lionlibrary.domain.model.MediaType

data class ScannedFile(
    val uri: Uri,
    val displayName: String,
    val extension: String,
    val mediaType: MediaType,
    val parentFolderName: String
)
