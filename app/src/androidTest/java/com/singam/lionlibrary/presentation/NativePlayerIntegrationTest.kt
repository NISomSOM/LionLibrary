package com.singam.lionlibrary.presentation

import android.net.Uri
import android.util.Log
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.inspector.MetadataRetriever
import androidx.media3.ui.PlayerView
import androidx.test.platform.app.InstrumentationRegistry
import com.singam.lionlibrary.presentation.player.engine.*
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.videolan.libvlc.util.VLCVideoLayout

/**
 * Native integration only. These tests never open the library database or save watch history. Break
 * vectors: pause before surface, early resume, paused seek, repeated release, inaccessible SAF URI,
 * HDR metadata lost between extractor and decoder, subtitle resource leaks.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class NativePlayerIntegrationTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun exoSafResumePauseSeekAndRelease() = playback(EngineType.EXOPLAYER)

    @Test fun vlcSafResumePauseSeekAndRelease() = playback(EngineType.LIBVLC)

    private fun playback(type: EngineType) = runBlocking {
        val fixture = fixture("silent-black-25s.mp4")
        val sidecar =
            File(fixture.parentFile, "subtitle.srt").apply {
                writeText("1\n00:00:00,000 --> 00:00:24,000\nPlayback fixture\n")
            }
        val engine =
            withContext(Dispatchers.Main) { AndroidPlayerEngineFactory(context).create(type) }
        try {
            withContext(Dispatchers.Main) {
                engine.setMedia(uri(fixture), uri(sidecar))
                engine.seekTo(4_000)
                engine.play()
                engine.pause() // Must cancel autoplay even though no surface exists yet.
            }
            attach(engine)
            delay(800)
            assertFalse(
                "$type autoplay survived pause before surface",
                engine.state.value.isPlaying,
            )
            withContext(Dispatchers.Main) { engine.play() }
            awaitPosition(engine, 4_500)
            withContext(Dispatchers.Main) {
                assertTrue(engine.durationMs >= 24_000)
                assertNull(engine.state.value.error)
                engine.pause()
                engine.pause() // Duplicate lifecycle pause must remain paused.
            }
            withTimeout(5_000) { engine.state.first { !it.isPlaying } }
            withContext(Dispatchers.Main) { engine.seekTo(10_000) }
            awaitPosition(engine, 9_500)
            assertFalse(engine.state.value.isPlaying)
            withContext(Dispatchers.Main) {
                assertTrue(
                    "$type sidecar subtitle missing",
                    engine.getSubtitleTracks().isNotEmpty(),
                )
                engine.selectSubtitleTrack(null)
                engine.play()
            }
            awaitPosition(engine, 10_500)
            report(type, engine)
        } finally {
            withContext(Dispatchers.Main) {
                engine.release()
                engine.release()
            }
            assertFalse(
                "VLC subtitle temp leak",
                context.cacheDir.listFiles().orEmpty().any {
                    it.name.startsWith("vlc_sub_")
                },
            )
            fixture.delete()
            sidecar.delete()
        }
    }

    @Test fun exoHdr10Decode() = hdrPlayback(EngineType.EXOPLAYER)

    @Test fun vlcHdr10Decode() = hdrPlayback(EngineType.LIBVLC)

    private fun hdrPlayback(type: EngineType) = runBlocking {
        val fixture = fixture("hdr10-720p.mp4")
        val engine =
            withContext(Dispatchers.Main) { AndroidPlayerEngineFactory(context).create(type) }
        try {
            val format =
                MetadataRetriever.Builder(context, MediaItem.fromUri(uri(fixture))).build().use {
                    val groups = it.retrieveTrackGroups().get()
                    (0 until groups.length)
                        .map { n -> groups.get(n) }
                        .first { group -> group.type == C.TRACK_TYPE_VIDEO }
                        .getFormat(0)
                }
            assertEquals(C.COLOR_TRANSFER_ST2084, format.colorInfo?.colorTransfer)
            if (type == EngineType.EXOPLAYER) {
                org.junit.Assume.assumeTrue(
                    "HDR fixture exceeds this device's decoder capabilities",
                    androidx.media3.exoplayer.mediacodec.MediaCodecUtil.getDecoderInfosSoftMatch(
                            androidx.media3.exoplayer.mediacodec.MediaCodecSelector.DEFAULT,
                            format,
                            false,
                            false,
                        )
                        .any { it.isFormatSupported(context, format) },
                )
            }
            Log.i("LionPlaybackAudit", "$type HDR fixture $format")
            withContext(Dispatchers.Main) {
                engine.setMedia(uri(fixture), null)
                engine.play()
            }
            attach(engine)
            awaitPosition(engine, 1_000)
            withContext(Dispatchers.Main) {
                assertNull(engine.state.value.error)
                engine.pause()
            }
            report(type, engine)
            Log.i("LionPlaybackAudit", "HDR_OUTPUT_READY=$type")
            delay(2_000)
        } finally {
            withContext(Dispatchers.Main) { engine.release() }
            fixture.delete()
        }
    }

    @Test
    fun inspectorPreservesHlgMetadata() = runBlocking {
        val fixture = fixture("hlg10-color-test.mp4")
        try {
            MetadataRetriever.Builder(context, MediaItem.fromUri(uri(fixture))).build().use {
                val groups = it.retrieveTrackGroups().get()
                val video =
                    (0 until groups.length)
                        .map { n -> groups.get(n) }
                        .first { group -> group.type == C.TRACK_TYPE_VIDEO }
                        .getFormat(0)
                assertEquals(C.COLOR_TRANSFER_HLG, video.colorInfo?.colorTransfer)
            }
        } finally {
            fixture.delete()
        }
    }

    @Test
    fun vlcMissingSafFileFailsWithoutStartingPlayback() = runBlocking {
        val engine =
            withContext(Dispatchers.Main) {
                AndroidPlayerEngineFactory(context).create(EngineType.LIBVLC)
            }
        try {
            withContext(Dispatchers.Main) {
                engine.setMedia(
                    Uri.parse(
                        "content://${context.packageName}.playback-fixtures/fixtures/nonexistent.mp4"
                    ),
                    null,
                )
                engine.play()
            }
            assertEquals(PlaybackFailure.FILE_ACCESS, engine.state.value.error)
            assertFalse(engine.state.value.isPlaying)
        } finally {
            withContext(Dispatchers.Main) { engine.release() }
        }
    }

    private fun fixture(name: String): File {
        val dir = File(context.cacheDir, "playback-fixtures").apply { mkdirs() }
        return File(dir, name).also { file ->
            instrumentation.context.assets.open("playback/$name").use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
        }
    }

    private fun uri(file: File) =
        FileProvider.getUriForFile(context, "${context.packageName}.playback-fixtures", file)

    private fun attach(engine: LionPlayerEngine) {
        if (engine is ExoPlayerEngine) {
            instrumentation.runOnMainSync {
                engine.exoPlayer.addAnalyticsListener(
                    object : androidx.media3.exoplayer.analytics.AnalyticsListener {
                        override fun onVideoDecoderInitialized(
                            eventTime:
                                androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                            decoderName: String,
                            initializedTimestampMs: Long,
                            initializationDurationMs: Long,
                        ) {
                            Log.i(
                                "LionPlaybackAudit",
                                "EXOPLAYER decoder=$decoderName initializationMs=$initializationDurationMs",
                            )
                        }

                        override fun onVideoInputFormatChanged(
                            eventTime:
                                androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                            format: androidx.media3.common.Format,
                            decoderReuseEvaluation:
                                androidx.media3.exoplayer.DecoderReuseEvaluation?,
                        ) {
                            Log.i("LionPlaybackAudit", "EXOPLAYER decoded-format=$format")
                        }
                    }
                )
            }
        }
        compose.setContent {
            AndroidView(
                factory = { ctx ->
                    if (engine.engineType == EngineType.EXOPLAYER)
                        PlayerView(ctx).apply { useController = false }
                    else VLCVideoLayout(ctx)
                },
                update = { engine.attachToView(it) },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    private suspend fun awaitPosition(engine: LionPlayerEngine, position: Long) =
        withTimeout(25_000) {
            while (withContext(Dispatchers.Main) { engine.currentPositionMs < position }) {
                assertNull(engine.state.value.error)
                delay(50)
            }
        }

    private suspend fun report(type: EngineType, engine: LionPlayerEngine) =
        withContext(Dispatchers.Main) {
            val counters = (engine as? ExoPlayerEngine)?.exoPlayer?.videoDecoderCounters
            counters?.ensureUpdated()
            Log.i(
                "LionPlaybackAudit",
                "$type playing=${engine.state.value.isPlaying} position=${engine.currentPositionMs} duration=${engine.durationMs} rendered=${counters?.renderedOutputBufferCount} dropped=${counters?.droppedBufferCount}",
            )
            if (counters != null)
                assertTrue("Decoder did not render a frame", counters.renderedOutputBufferCount > 0)
            if (engine is LibVlcPlayerEngine) {
                val stats = engine.videoStats()
                Log.i(
                    "LionPlaybackAudit",
                    "LIBVLC decoded=${stats?.decodedVideo} displayed=${stats?.displayedPictures} lost=${stats?.lostPictures}",
                )
                assertTrue("VLC did not display a video frame", (stats?.displayedPictures ?: 0) > 0)
            }
        }
}
