package com.singam.lionlibrary.presentation.player

import android.app.Activity
import android.media.AudioManager
import android.provider.Settings
import androidx.compose.runtime.Stable
import kotlin.math.roundToInt

/** Window brightness and media volume as 0..1 levels; owned by PlayerRoot, used by gestures. */
@Stable
interface PlayerLevelController {
    fun brightness(): Float

    fun setBrightness(level: Float)

    fun volume(): Float

    /** Sets media volume, rounding to the device's steps; returns the level actually applied. */
    fun setVolume(level: Float): Float
}

/**
 * Platform implementation. Brightness overrides only this window (PlayerRoot restores the original
 * value on exit); volume changes the system media stream without showing the system panel.
 */
internal class AndroidPlayerLevelController(
    private val activity: Activity?,
    private val audioManager: AudioManager,
) : PlayerLevelController {
    private val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

    override fun brightness(): Float {
        val window = activity?.window ?: return DEFAULT_BRIGHTNESS
        val own = window.attributes.screenBrightness
        if (own >= 0f) return own.coerceIn(0f, 1f)
        // Window follows the system setting: start from it.
        return try {
            Settings.System.getInt(activity.contentResolver, Settings.System.SCREEN_BRIGHTNESS) /
                255f
        } catch (_: Settings.SettingNotFoundException) {
            DEFAULT_BRIGHTNESS
        }
    }

    override fun setBrightness(level: Float) {
        val window = activity?.window ?: return
        window.attributes = window.attributes.apply { screenBrightness = level.coerceIn(0f, 1f) }
    }

    override fun volume(): Float =
        if (maxVolume > 0)
            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) / maxVolume.toFloat()
        else 0f

    override fun setVolume(level: Float): Float {
        if (maxVolume <= 0) return 0f
        val steps = (level.coerceIn(0f, 1f) * maxVolume).roundToInt()
        if (steps != audioManager.getStreamVolume(AudioManager.STREAM_MUSIC))
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, steps, 0)
        return steps / maxVolume.toFloat()
    }

    private companion object {
        const val DEFAULT_BRIGHTNESS = 0.5f
    }
}

/** For previews and tests: holds levels in memory. */
class InMemoryPlayerLevelController(
    private var brightnessLevel: Float = .5f,
    private var volumeLevel: Float = .5f,
) : PlayerLevelController {
    override fun brightness() = brightnessLevel

    override fun setBrightness(level: Float) {
        brightnessLevel = level.coerceIn(0f, 1f)
    }

    override fun volume() = volumeLevel

    override fun setVolume(level: Float): Float {
        volumeLevel = level.coerceIn(0f, 1f)
        return volumeLevel
    }
}
