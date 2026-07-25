package com.singam.lionlibrary.domain.usecase

import com.singam.lionlibrary.domain.model.ScanProgress
import kotlinx.coroutines.flow.Flow

// Scan the library.
interface ScanLibraryUseCase {
    operator fun invoke(): Flow<ScanProgress>
}

