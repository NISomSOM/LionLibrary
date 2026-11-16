package com.singam.lionlibrary.domain.model

/**
 * Playback engine chosen per file. The scanner stores [name] in Room (`preferredEngine`), so the
 * constant names are persisted values and must not change.
 */
enum class EngineType {
    EXOPLAYER,
    LIBVLC,
}
