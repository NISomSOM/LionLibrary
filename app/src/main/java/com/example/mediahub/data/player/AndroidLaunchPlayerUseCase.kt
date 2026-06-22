package com.example.mediahub.data.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.mediahub.domain.usecase.LaunchPlayerUseCase

/**
 * Implementation of [LaunchPlayerUseCase] that builds a generic
 * ACTION_VIEW intent to play the video.
 */
class AndroidLaunchPlayerUseCase(
    private val context: Context
) : LaunchPlayerUseCase {

    override fun invoke(fileUri: Uri, positionMs: Long): Intent {
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(fileUri, "video/*")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
    }
}
