package com.singam.lionlibrary.presentation

import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.singam.lionlibrary.domain.model.*
import com.singam.lionlibrary.domain.repository.*
import com.singam.lionlibrary.domain.usecase.LaunchPlayerUseCase
import com.singam.lionlibrary.presentation.player.*
import com.singam.lionlibrary.presentation.player.engine.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test

class PlayerSessionIntegrationTest {
    @Test
    fun switchingFilesHonorsEngineAndSavesCapturedIdentity() = runBlocking {
        withContext(Dispatchers.Main) {
            val repo = PlaybackFake()
            val factory = EngineFactoryFake()
            val focus = FocusFake()
            val vm =
                PlayerViewModel(
                    SavedStateHandle(mapOf("mediaType" to "TV_SHOW", "mediaId" to 11L)),
                    repo,
                    object : LaunchPlayerUseCase {
                        override fun invoke(fileUri: Uri, positionMs: Long) = Intent()
                    },
                    SettingsFake(),
                    factory,
                    focus,
                    TrackPreferencesFake(),
                    SkipSegmentsFake(),
                )
            val store = ViewModelStore()
            store.put("player", vm)
            try {
                withTimeout(10_000) { vm.state.first { it.isPlaying } }
                factory.engines.last().position = 500
                vm.onAction(PlayerAction.OnPlayNext)
                withTimeout(10_000) { vm.state.first { it.session == 2L && it.isPlaying } }
                assertEquals(listOf(EngineType.EXOPLAYER, EngineType.LIBVLC), factory.types)
                assertTrue(factory.engines.first().released)
                assertEquals(11L, repo.saved.first().episodeId)
                assertEquals(500L, repo.saved.first().positionMs)
                factory.engines.last().position = 200
                vm.onAction(PlayerAction.Pause)
                assertFalse(factory.engines.last().state.value.isPlaying)
                assertEquals(12L, repo.saved.last().episodeId)
            } finally {
                store.clear()
            }
        }
    }

    @Test
    fun endedEpisodeCannotBeRewrittenAsUnfinishedDuringExit() = runBlocking {
        withContext(Dispatchers.Main) {
            val repo = PlaybackFake(single = true)
            val factory = EngineFactoryFake()
            val vm =
                PlayerViewModel(
                    SavedStateHandle(mapOf("mediaType" to "TV_SHOW", "mediaId" to 11L)),
                    repo,
                    object : LaunchPlayerUseCase {
                        override fun invoke(fileUri: Uri, positionMs: Long) = Intent()
                    },
                    SettingsFake(),
                    factory,
                    FocusFake(),
                    TrackPreferencesFake(),
                    SkipSegmentsFake(),
                )
            val store = ViewModelStore()
            store.put("player", vm)
            withTimeout(10_000) { vm.state.first { it.isPlaying } }
            factory.engines.last().finish()
            withTimeout(10_000) { vm.state.first { it.playbackState == EnginePlaybackState.ENDED } }
            store.clear()
            assertTrue(repo.saved.isNotEmpty())
            assertTrue(repo.saved.all { it.ended })
        }
    }

    // Attack tracks discovered after READY and user choices racing later discovery.
    @Test
    fun lateTracksApplyDefaultsOnceAndKeepManualSubtitleOff() = runBlocking {
        withContext(Dispatchers.Main) {
            val repo = PlaybackFake(single = true)
            val factory = EngineFactoryFake()
            val vm =
                PlayerViewModel(
                    SavedStateHandle(mapOf("mediaType" to "TV_SHOW", "mediaId" to 11L)),
                    repo,
                    object : LaunchPlayerUseCase {
                        override fun invoke(fileUri: Uri, positionMs: Long) = Intent()
                    },
                    SettingsFake(),
                    factory,
                    FocusFake(),
                    TrackPreferencesFake(),
                    SkipSegmentsFake(),
                )
            val store = ViewModelStore().apply { put("player", vm) }
            try {
                withTimeout(10_000) { vm.state.first { it.isPlaying } }
                val engine = factory.engines.last()
                engine.discoverTracks()
                withTimeout(10_000) { vm.state.first { it.subtitleTracks.isNotEmpty() } }
                assertEquals("english", engine.selectedAudio)
                assertEquals("full", engine.selectedSubtitle)
                vm.onAction(PlayerAction.SelectSubtitle(null))
                vm.onAction(PlayerAction.SelectAudio("japanese"))
                engine.discoverTracks()
                yield()
                assertNull(engine.selectedSubtitle)
                assertEquals("japanese", engine.selectedAudio)
            } finally {
                store.clear()
            }
        }
    }
}

private class PlaybackFake(val single: Boolean = false) : PlaybackRepository {
    val saved = mutableListOf<PlaybackSnapshot>()
    private val first =
        PlaybackTarget(
            1,
            11,
            MediaType.TV_SHOW,
            "Show",
            "content://fixture/one",
            null,
            "EXOPLAYER",
            season = 1,
            episode = 1,
        )
    private val second =
        first.copy(
            episodeId = 12,
            filePath = "content://fixture/two",
            preferredEngine = "LIBVLC",
            episode = 2,
        )

    override suspend fun target(type: MediaType, id: Long) = first

    override suspend fun adjacent(target: PlaybackTarget, next: Boolean) =
        if (!single && next && target.episodeId == 11L) second else null

    override suspend fun progress(target: PlaybackTarget): WatchProgress? = null

    override fun save(snapshot: PlaybackSnapshot): Deferred<OperationResult<Unit, DataError>> {
        saved += snapshot
        return CompletableDeferred(OperationResult.Success(Unit))
    }
}

private class EngineFactoryFake : PlayerEngineFactory {
    val types = mutableListOf<EngineType>()
    val engines = mutableListOf<EngineFake>()

    override suspend fun create(type: EngineType): LionPlayerEngine {
        types += type
        return EngineFake(type).also { engines += it }
    }
}

private class EngineFake(override val engineType: EngineType) : LionPlayerEngine {
    private val mutable = MutableStateFlow(EngineState())
    override val state = mutable.asStateFlow()
    var position = 0L
    var released = false
    override val currentPositionMs
        get() = position

    override val durationMs = 1000L

    override suspend fun setMedia(uri: Uri, subtitleUri: Uri?) {
        position = 0
        mutable.value = EngineState(playbackState = EnginePlaybackState.READY, durationMs = 1000)
    }

    override fun play() {
        mutable.value = mutable.value.copy(isPlaying = true)
    }

    override fun pause() {
        mutable.value = mutable.value.copy(isPlaying = false)
    }

    override fun stop() {
        mutable.value = EngineState()
    }

    override fun release() {
        released = true
    }

    override fun seekTo(positionMs: Long) {
        position = positionMs
    }

    private var audio = emptyList<EngineTrackInfo>()
    private var subtitles = emptyList<EngineTrackInfo>()
    var selectedAudio: String? = null
    var selectedSubtitle: String? = null

    fun discoverTracks() {
        audio =
            listOf(
                EngineTrackInfo("japanese", "ja", false),
                EngineTrackInfo("english", "en", false),
            )
        subtitles =
            listOf(
                EngineTrackInfo("signs", "en ? Signs & Songs", false),
                EngineTrackInfo("full", "en ? Full dialogue", false),
            )
        mutable.value = mutable.value.copy(trackRevision = mutable.value.trackRevision + 1)
    }

    override fun getAudioTracks() = audio

    override fun getSubtitleTracks() = subtitles

    override fun selectAudioTrack(id: String) {
        selectedAudio = id
    }

    override fun selectSubtitleTrack(id: String?) {
        selectedSubtitle = id
    }

    override fun resetToDefaultTrackSelection() {}

    override fun attachToView(container: ViewGroup) {}

    fun finish() {
        position = 1000
        mutable.value =
            mutable.value.copy(isPlaying = false, playbackState = EnginePlaybackState.ENDED)
    }
}

private class FocusFake : PlaybackAudioFocus {
    override fun request(onLoss: () -> Unit) = true

    override fun abandon() {}
}

internal class SettingsFake : SettingsRepository {
    override val tmdbApiKey = flowOf("")
    override val moviesFolderUri = flowOf("")
    override val showsFolderUri = flowOf("")
    override val lastScanTime = flowOf(0L)
    override val forceLibVlc = flowOf(false)
    override val forceExternalPlayer = flowOf(false)
    override val onlineSkipTimes = flowOf(false)
    override val keyVerified = flowOf(false)
    override val setupDismissed = flowOf(false)
    override val scanSummary = flowOf(ScanSummary())

    override suspend fun setTmdbApiKey(key: String) {}

    override suspend fun setMoviesFolderUri(uri: String) {}

    override suspend fun setShowsFolderUri(uri: String) {}

    override suspend fun setLastScanTime(time: Long) {}

    override suspend fun setForceLibVlc(enabled: Boolean) {}

    override suspend fun setForceExternalPlayer(enabled: Boolean) {}

    override suspend fun setOnlineSkipTimes(enabled: Boolean) {}

    override suspend fun setKeyVerified(verified: Boolean) {}

    override suspend fun setSetupDismissed(dismissed: Boolean) {}

    override suspend fun setScanSummary(summary: ScanSummary) {}
}

private class TrackPreferencesFake :
    com.singam.lionlibrary.domain.repository.TrackPreferenceRepository {
    val saved = mutableMapOf<Long, com.singam.lionlibrary.domain.model.TrackPreference>()

    override suspend fun get(mediaId: Long) = saved[mediaId]

    override suspend fun save(
        mediaId: Long,
        preference: com.singam.lionlibrary.domain.model.TrackPreference,
    ) {
        saved[mediaId] = preference
    }
}

/** No online skip times; these tests cover sessions and progress, not provider lookups. */
private class SkipSegmentsFake : SkipSegmentRepository {
    override suspend fun onlineSegments(
        key: SkipLookupKey,
        durationMs: Long,
        types: Set<SkipSegmentType>,
    ): OperationResult<List<SkipSegment>, DataError> = OperationResult.Success(emptyList())
}
