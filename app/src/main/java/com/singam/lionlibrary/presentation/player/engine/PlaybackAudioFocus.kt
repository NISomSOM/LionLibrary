package com.singam.lionlibrary.presentation.player.engine

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build

interface PlaybackAudioFocus {
    fun request(onLoss: () -> Unit): Boolean

    fun abandon()
}

class AndroidPlaybackAudioFocus(context: Context) : PlaybackAudioFocus {
    private val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var onLoss: (() -> Unit)? = null
    private val listener = AudioManager.OnAudioFocusChangeListener { change ->
        if (change < 0) onLoss?.invoke()
    }
    private var request: AudioFocusRequest? = null

    @Suppress("DEPRECATION")
    override fun request(onLoss: () -> Unit): Boolean {
        this.onLoss = onLoss
        val result =
            if (Build.VERSION.SDK_INT >= 26) {
                val focus =
                    AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                                .build()
                        )
                        .setOnAudioFocusChangeListener(listener)
                        .setWillPauseWhenDucked(true)
                        .build()
                request = focus
                manager.requestAudioFocus(focus)
            } else
                manager.requestAudioFocus(
                    listener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN,
                )
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    @Suppress("DEPRECATION")
    override fun abandon() {
        if (Build.VERSION.SDK_INT >= 26) request?.let { manager.abandonAudioFocusRequest(it) }
        else manager.abandonAudioFocus(listener)
        request = null
        onLoss = null
    }
}
