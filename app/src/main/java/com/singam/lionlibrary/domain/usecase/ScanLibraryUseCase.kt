package com.singam.lionlibrary.domain.usecase

import com.singam.lionlibrary.domain.model.ScanProgress
import kotlinx.coroutines.flow.Flow

// Trigger a library scan.
interface ScanLibraryUseCase {
    operator fun invoke(): Flow<ScanProgress>
}

