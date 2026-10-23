package com.singam.lionlibrary.presentation.player.engine

/** Commands remain pending until native playback can honour them. Owned by the main thread. */
internal class PendingPlayback {
    var playRequested: Boolean = false
        private set

    private var seekMs: Long? = null

    fun play() {
        playRequested = true
    }

    fun pause() {
        playRequested = false
    }

    fun seek(positionMs: Long) {
        seekMs = positionMs.coerceAtLeast(0)
    }

    fun positionWhenSeekable(seekable: Boolean): Long? {
        if (!seekable) return null
        return seekMs.also { seekMs = null }
    }

    fun reset() {
        playRequested = false
        seekMs = null
    }
}
