package com.example.mediahub.data.player

import android.content.Intent
import android.net.Uri

/**
 * Utility functions for external video player integration.
 */
object PlayerUtils {

    /**
     * Creates a generic ACTION_VIEW intent for a video file,
     * allowing the system chooser to pick a player.
     */
    fun genericPlayerIntent(fileUri: Uri): Intent {
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(fileUri, "video/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
