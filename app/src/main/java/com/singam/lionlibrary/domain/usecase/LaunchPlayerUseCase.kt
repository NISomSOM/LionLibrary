package com.singam.lionlibrary.domain.usecase

import android.content.Intent
import android.net.Uri

// Interface for playing a video file.
interface LaunchPlayerUseCase {
    operator fun invoke(fileUri: Uri, positionMs: Long = 0): Intent
}

