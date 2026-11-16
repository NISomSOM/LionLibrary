package com.singam.lionlibrary.presentation.player.engine

import android.content.Context
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ForwardingRenderer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.text.TextOutput

/**
 * Media3 has no subtitle offset, so the text renderer is asked for the cues at (playback position −
 * delay): a positive delay shows subtitles later, a negative one earlier. Earlier works as far
 * ahead as the player has buffered, which covers the ±60 s range the UI allows for sidecar files
 * and normal embedded tracks.
 */
@OptIn(UnstableApi::class)
internal class DelayedTextRenderer(renderer: Renderer) : ForwardingRenderer(renderer) {
    // Written on the main thread, read on the playback thread.
    @Volatile var delayUs: Long = 0L

    override fun render(positionUs: Long, elapsedRealtimeUs: Long) {
        super.render(positionUs - delayUs, elapsedRealtimeUs)
    }
}

/** Default renderers with every text renderer wrapped so its timing can be shifted. */
@OptIn(UnstableApi::class)
internal class DelayableRenderersFactory(context: Context) : DefaultRenderersFactory(context) {
    val textRenderers = mutableListOf<DelayedTextRenderer>()

    override fun buildTextRenderers(
        context: Context,
        output: TextOutput,
        outputLooper: Looper,
        extensionRendererMode: Int,
        out: ArrayList<Renderer>,
    ) {
        val first = out.size
        super.buildTextRenderers(context, output, outputLooper, extensionRendererMode, out)
        for (index in first until out.size) {
            out[index] = DelayedTextRenderer(out[index]).also(textRenderers::add)
        }
    }
}
