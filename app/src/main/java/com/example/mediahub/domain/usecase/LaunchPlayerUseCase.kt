package com.example.mediahub.domain.usecase

import android.content.Intent
import android.net.Uri

/**
 * Interface for the launch player use case.
 * Implementation lives in the data layer because it requires Android Context.
 */
interface LaunchPlayerUseCase {
    operator fun invoke(fileUri: Uri, positionMs: Long = 0): Intent
}
