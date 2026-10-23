package com.singam.lionlibrary.presentation.player.engine

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface PlayerEngineFactory {
    suspend fun create(type: EngineType): LionPlayerEngine
}

class AndroidPlayerEngineFactory(private val context: Context) : PlayerEngineFactory {
    override suspend fun create(type: EngineType) =
        when (type) {
            EngineType.EXOPLAYER -> ExoPlayerEngine(context)
            EngineType.LIBVLC -> withContext(Dispatchers.IO) { LibVlcPlayerEngine(context) }
        }
}
