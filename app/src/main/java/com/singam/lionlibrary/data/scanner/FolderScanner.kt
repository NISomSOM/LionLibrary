package com.singam.lionlibrary.data.scanner

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.singam.lionlibrary.domain.model.MediaType
import com.singam.lionlibrary.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FolderPermissionException(message: String) : Exception(message)

// Walks through SAF folders to find video files.
class FolderScanner(private val context: Context) {

    private val seasonPattern1 = Regex("""^[Ss]eason\s*\d+$""")
    private val seasonPattern2 = Regex("""^[Ss]\d+$""")

    // Scans a folder (SAF URI) and returns all video files we care about.
    suspend fun scanFolder(treeUri: Uri, mediaType: MediaType): List<ScannedFile> =
        withContext(Dispatchers.IO) {
            try {
                val root = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext emptyList()
                val results = mutableListOf<ScannedFile>()
                traverseDirectory(root, mediaType, results)
                results
            } catch (e: SecurityException) {
                throw FolderPermissionException("Permission revoked for folder: $treeUri")
            }
        }

    // Recursive walk.
    private fun traverseDirectory(
        directory: DocumentFile,
        mediaType: MediaType,
        results: MutableList<ScannedFile>,
        parentName: String? = null,
        grandParentName: String? = null
    ) {
        val children = directory.listFiles()
        for (file in children) {
            if (file.isDirectory) {
                traverseDirectory(file, mediaType, results, directory.name, parentName)
            } else {
                val name = file.name ?: continue
                val extension = name.substringAfterLast('.', "").lowercase()

                if (extension in Constants.SUPPORTED_VIDEO_EXTENSIONS) {
                    val pName = directory.name?.trim() ?: ""
                    val gpName = parentName?.trim() ?: ""
                    val isSeasonFolder = seasonPattern1.matches(pName) || seasonPattern2.matches(pName)
                    val parentFolderName = if (isSeasonFolder && gpName.isNotEmpty()) {
                        gpName
                    } else {
                        pName
                    }

                    results.add(
                        ScannedFile(
                            uri = file.uri,
                            displayName = name,
                            extension = extension,
                            mediaType = mediaType,
                            parentFolderName = parentFolderName
                        )
                    )
                }
            }
        }
    }
}

