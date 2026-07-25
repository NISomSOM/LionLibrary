package com.singam.lionlibrary.presentation.player.engine

import android.content.Context
import android.system.Os
import org.videolan.libvlc.LibVLC
import java.io.File

/**
 * Provide a LibVLC singleton.
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

    /** Set up fontconfig configuration before creating a LibVLC instance. */
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

    /** Initialize fontconfig and create LibVLC singleton in the background. */
    fun prewarm(context: Context) {
        val appContext = context.applicationContext

        Thread {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
            try {
                // Configure fontconfig FIRST, before LibVLC touches it.
                initFontconfig(appContext)
                android.util.Log.i("LibVlcProvider", "Starting LibVLC singleton prewarm")
                getSharedInstance(appContext)
                android.util.Log.i("LibVlcProvider", "LibVLC singleton prewarm complete")
            } catch (e: Exception) {
                android.util.Log.e("LibVlcProvider", "LibVLC singleton prewarm failed", e)
            }
        }.start()
    }

    /** Get the LibVLC singleton. */
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
