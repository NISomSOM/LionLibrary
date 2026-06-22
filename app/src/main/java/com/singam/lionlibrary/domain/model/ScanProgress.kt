package com.singam.lionlibrary.domain.model

data class ScanProgress(
    val total: Int,
    val processed: Int,
    val currentFile: String,
    val status: ScanStatus
)

enum class ScanStatus {
    SCANNING,
    MATCHED,
    UNIDENTIFIED,
    SKIPPED,
    ERROR,
    COMPLETE,
    API_KEY_MISSING,
    INVALID_API_KEY,
    NO_INTERNET,
    PERMISSION_REVOKED
}

