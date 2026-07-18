package com.singam.lionlibrary.presentation.player.engine

import android.content.Context
import android.system.Os
import org.videolan.libvlc.LibVLC
import java.io.File

/**
 * Singleton provider for LibVLC.
 *
 * Android 16 on certain devices (e.g. Vivo) has SELinux rules that block
 * fontconfig from persisting its cache (denying the `link` syscall for
 * atomic renames of .cache-7 files). Without a persistent cache, fontconfig
 * rescans ALL system fonts (~30 seconds) every time LibVLC plays media.
 *
 * The fix: we create a minimal fonts.conf that tells fontconfig to look at
 * exactly ONE font file and set FONTCONFIG_FILE env var BEFORE LibVLC loads.
 * This makes fontconfig init instant instead of 30 seconds.
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
     * Initializes fontconfig + LibVLC on a background thread.
     * Should be called from Application.onCreate().
     */
    fun prewarm(context: Context) {
        val appContext = context.applicationContext
        // Set fontconfig env immediately on the calling thread (main thread)
        // so it's ready before ANY LibVLC code runs.
        initFontconfig(appContext)

        Thread {
            try {
                android.util.Log.i("LibVlcProvider", "Starting LibVLC singleton prewarm")
                val vlc = getSharedInstance(appContext)
                
                // CRITICAL FIX FOR 30-SECOND PLAYBACK DELAY:
                // VLC's text_renderer/freetype/fonts/android.c hardcodes scanning the entire
                // /system/fonts/ directory into Fontconfig, overriding our minimal fonts.conf.
                // This scan takes ~30 seconds and is triggered lazily when the first video is played.
                // To prevent the user from waiting 30 seconds on playback, we intentionally trigger
                // the scan right now in the background by playing a 1-pixel dummy GIF.
                // The cache will remain in the LibVLC singleton's memory for all subsequent playbacks.
                
                val dummyGif = File(appContext.cacheDir, "vlc_dummy_prewarm.gif")
                if (!dummyGif.exists()) {
                    // 1x1 transparent GIF (43 bytes)
                    val base64Gif = "R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7"
                    dummyGif.writeBytes(android.util.Base64.decode(base64Gif, android.util.Base64.DEFAULT))
                }
                
                val dummyPlayer = org.videolan.libvlc.MediaPlayer(vlc)
                val dummyMedia = org.videolan.libvlc.Media(vlc, dummyGif.absolutePath)
                dummyPlayer.media = dummyMedia
                
                // We use an EventListener to release resources once the scan is finished and the GIF "plays".
                dummyPlayer.setEventListener(object : org.videolan.libvlc.MediaPlayer.EventListener {
                    override fun onEvent(event: org.videolan.libvlc.MediaPlayer.Event?) {
                        if (event?.type == org.videolan.libvlc.MediaPlayer.Event.Playing || 
                            event?.type == org.videolan.libvlc.MediaPlayer.Event.EndReached ||
                            event?.type == org.videolan.libvlc.MediaPlayer.Event.EncounteredError) {
                            
                            android.util.Log.i("LibVlcProvider", "Background fontconfig scan completed. (Event: ${event.type})")
                            dummyPlayer.setEventListener(null)
                            
                            // Must release on a separate thread to avoid deadlocking the LibVLC event callback
                            Thread {
                                try {
                                    dummyPlayer.stop()
                                    dummyPlayer.release()
                                    dummyMedia.release()
                                } catch (e: Exception) {
                                    android.util.Log.e("LibVlcProvider", "Error releasing dummy player", e)
                                }
                            }.start()
                        }
                    }
                })
                
                android.util.Log.i("LibVlcProvider", "Triggering background fontconfig scan...")
                // This play() call is asynchronous, but the heavy lifting (fontconfig scan) 
                // will be handled by VLC's internal threads.
                dummyPlayer.play()
                
                android.util.Log.i("LibVlcProvider", "Finished LibVLC singleton prewarm setup")
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
