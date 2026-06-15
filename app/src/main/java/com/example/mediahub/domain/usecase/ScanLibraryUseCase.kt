package com.example.mediahub.domain.usecase

import com.example.mediahub.domain.model.ScanProgress
import kotlinx.coroutines.flow.Flow

/**
 * Interface for the scan library use case.
 * Implementation lives in the data layer because it requires Android dependencies
 * (SAF DocumentFile, Context for content resolver).
 */
interface ScanLibraryUseCase {
    operator fun invoke(): Flow<ScanProgress>
}
