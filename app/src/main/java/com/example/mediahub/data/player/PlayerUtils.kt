package com.example.mediahub.data.player

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.example.mediahub.util.Constants

/**
 * Utility functions for external video player integration.
 */
object PlayerUtils {

    /**
     * Checks if a player app is installed on the device.
     */
    fun isPlayerInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * Creates an intent to open the Play Store listing for a given package.
     */
    fun playStoreIntent(packageName: String): Intent {
        return Intent(
            Intent.ACTION_VIEW,
            Uri.parse("market://details?id=$packageName")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

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

    /**
     * Creates a VLC-specific intent to play a video file.
     */
    fun vlcIntent(fileUri: Uri, positionMs: Long = 0): Intent {
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(fileUri, "video/*")
            setPackage(Constants.VLC_PACKAGE)
            putExtra("position", positionMs)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /**
     * Creates an MX Player-specific intent to play a video file.
     */
    fun mxPlayerIntent(fileUri: Uri, positionMs: Long = 0): Intent {
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(fileUri, "video/*")
            setPackage(Constants.MX_PLAYER_PACKAGE)
            putExtra("position", positionMs.toInt())
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
