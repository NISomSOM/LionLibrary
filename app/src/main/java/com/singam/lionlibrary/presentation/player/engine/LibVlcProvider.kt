package com.singam.lionlibrary.presentation.player.engine

import android.content.Context
import android.system.Os
import org.videolan.libvlc.LibVLC
import java.io.File

/**
 * Singleton provider for LibVLC.
 *
 * Fontconfig (used internally by libVLC's freetype subtitle renderer) scans
 * every font in its configured directories on first use. On Android, the
 * default directory is /system/fonts/ which contains hundreds of files — this
 * scan takes ~30 seconds on some devices and is further complicated by SELinux
 * rules on certain vendors (e.g. Vivo) that block fontconfig's cache persistence.
 *
 * The fix: [initFontconfig] creates a minimal fonts.conf that points fontconfig
 * at a directory containing exactly ONE copied font file, with a cache directory
 * inside app-private storage. This makes fontconfig init instant.
 *
 * [prewarm] is called from [LionLibraryApp.onCreate] to run this setup and
 * eagerly create the LibVLC singleton on a background thread, so everything is
 * ready before the user ever opens a video.
 */
object LibVlcProvider {
    @Volatile
    private var sharedVlc: LibVLC? = null
    private var fontconfigInitialized = false

    private val vlcOptions = arrayListOf(
        "--no-drop-late-frames",
        "--no-skip-frames",
        "--rtsp-tcp",
        "--aout=opensles",
        "--audio-time-stretch",
        "--avcodec-skiploopfilter=0",
        "--avcodec-skip-frame=0",
        "--avcodec-skip-idct=0",
        "--subsdec-encoding=UTF-8",
        "--no-sub-autodetect-file",
        "--freetype-font=/system/fonts/Roboto-Regular.ttf",
        "--stats",
        "--network-caching=1500",
        "--sout-keep"
    )

    /**
     * Creates a minimal fonts.conf in the app's files directory and sets
     * the FONTCONFIG_FILE environment variable so fontconfig reads it
     * instead of scanning the entire /system/fonts/ directory.
     *
     * MUST be called before any LibVLC instance is created.
     */
    private fun initFontconfig(context: Context) {
        if (fontconfigInitialized) return
        fontconfigInitialized = true

        try {
            val fontsDir = File(context.filesDir, "vlc_fontconfig")
            fontsDir.mkdirs()

            val cacheDir = File(fontsDir, "cache")
            cacheDir.mkdirs()

            val fontsConf = File(fontsDir, "fonts.conf")
            // Minimal fontconfig config:
            // - Point to a directory containing ONLY a single font file (via copy)
            // - No directory scanning of /system/fonts (that's the 30s delay)
            // - Cache directory inside our app's writable storage (no SELinux issues)
            // - rescan=0 to prevent re-indexing
            
            // Copy a single font file into our controlled directory so fontconfig
            // only indexes that one file, not hundreds in /system/fonts/
            val singleFontDir = File(fontsDir, "fonts")
            singleFontDir.mkdirs()
            val targetFont = File(singleFontDir, "Roboto-Regular.ttf")
            if (!targetFont.exists()) {
                try {
                    File("/system/fonts/Roboto-Regular.ttf").inputStream().use { src ->
                        targetFont.outputStream().use { dst -> src.copyTo(dst) }
                    }
                } catch (e: Exception) {
                    // If Roboto doesn't exist, try DroidSans
                    try {
                        File("/system/fonts/DroidSans.ttf").inputStream().use { src ->
                            targetFont.outputStream().use { dst -> src.copyTo(dst) }
                        }
                    } catch (_: Exception) {}
                }
            }
            
            fontsConf.writeText("""<?xml version="1.0"?>
<!DOCTYPE fontconfig SYSTEM "urn:fontconfig:fonts.dtd">
<fontconfig>
    <dir>${singleFontDir.absolutePath}</dir>
    <cachedir>${cacheDir.absolutePath}</cachedir>
    <config>
        <rescan>
            <int>0</int>
        </rescan>
    </config>
</fontconfig>
""")

            // Set environment variable BEFORE LibVLC touches fontconfig.
            // Os.setenv calls the native setenv(), which fontconfig reads.
            Os.setenv("FONTCONFIG_FILE", fontsConf.absolutePath, true)
            Os.setenv("FONTCONFIG_PATH", fontsDir.absolutePath, true)

            android.util.Log.i(
                "LibVlcProvider",
                "Fontconfig env set: FONTCONFIG_FILE=${fontsConf.absolutePath}"
            )
        } catch (e: Exception) {
            android.util.Log.e("LibVlcProvider", "Failed to init fontconfig", e)
        }
    }

    /**
     * Sets up fontconfig and eagerly creates the LibVLC singleton on a
     * background thread. Should be called from Application.onCreate().
     */
    fun prewarm(context: Context) {
        val appContext = context.applicationContext
        // Set fontconfig env immediately on the calling thread (main thread)
        // so it's ready before ANY LibVLC code runs.
        initFontconfig(appContext)

        Thread {
            try {
                android.util.Log.i("LibVlcProvider", "Starting LibVLC singleton prewarm")
                getSharedInstance(appContext)
                android.util.Log.i("LibVlcProvider", "LibVLC singleton prewarm complete")
            } catch (e: Exception) {
                android.util.Log.e("LibVlcProvider", "LibVLC singleton prewarm failed", e)
            }
        }.start()
    }

    /**
     * Retrieves the shared LibVLC instance, creating it synchronously if needed.
     */
    fun getSharedInstance(context: Context): LibVLC {
        // Ensure fontconfig is configured even if prewarm wasn't called
        initFontconfig(context)
        return sharedVlc ?: synchronized(this) {
            sharedVlc ?: LibVLC(context.applicationContext, vlcOptions).also {
                sharedVlc = it
            }
        }
    }
}
