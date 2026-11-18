package com.singam.lionlibrary.presentation

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.singam.lionlibrary.domain.model.*
import com.singam.lionlibrary.domain.repository.*
import com.singam.lionlibrary.domain.usecase.LaunchPlayerUseCase
import com.singam.lionlibrary.presentation.player.PlayerAction
import com.singam.lionlibrary.presentation.player.PlayerViewModel
import com.singam.lionlibrary.presentation.player.engine.*
import com.singam.lionlibrary.testing.FakeSettingsRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * JVM tests with fake engines (Robolectric only for `Uri`). Break vectors: hold-to-2x while locked,
 * paused or failed; a speed picked during the hold being lost or applied too early; release without
 * hold; position ticks leaking into the main state; chapters not reaching the seek bar; anime
 * defaults; a manual subtitle choice not remembered; completion not saved before auto-next; and an
 * untouched file creating history on exit.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class PlayerViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = PlaybackFake()
    private val factory = EngineFactoryFake()
    private val preferences = TrackPreferencesFake()
    private lateinit var vm: PlayerViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        if (::vm.isInitialized) vm.viewModelScope.cancel()
        Dispatchers.resetMain()
    }

    /**
     * runTest drains the scheduler when the body ends; the player's 500 ms ticker never idles, so
     * the ViewModel scope is cancelled inside the body.
     */
    private fun playerTest(body: suspend TestScope.() -> Unit) =
        runTest(dispatcher) {
            try {
                body()
            } finally {
                if (::vm.isInitialized) vm.viewModelScope.cancel()
            }
        }

    private fun TestScope.start(
        type: MediaType = MediaType.TV_SHOW,
        settings: FakeSettingsRepository = FakeSettingsRepository(),
    ): EngineFake {
        vm =
            PlayerViewModel(
                SavedStateHandle(mapOf("mediaType" to type.name, "mediaId" to 11L)),
                repository.also { it.type = type },
                object : LaunchPlayerUseCase {
                    override fun invoke(fileUri: Uri, positionMs: Long) = Intent()
                },
                settings.apply { onlineSkipTimes.value = false },
                factory,
                object : PlaybackAudioFocus {
                    override fun request(onLoss: () -> Unit) = true

                    override fun abandon() {}
                },
                preferences,
                object : SkipSegmentRepository {
                    override suspend fun onlineSegments(
                        key: SkipLookupKey,
                        durationMs: Long,
                        types: Set<SkipSegmentType>,
                    ): OperationResult<List<SkipSegment>, DataError> =
                        OperationResult.Success(emptyList())
                },
            )
        runCurrent()
        return factory.engines.last()
    }

    @Test
    fun holdPlaysAtDoubleSpeedAndReleaseRestoresTheChosenSpeed() = playerTest {
        val engine = start()
        vm.onAction(PlayerAction.OnSpeedSelect(1.5f))

        vm.onAction(PlayerAction.OnHoldSpeedChange(true))
        assertEquals(2f, engine.speed)
        assertTrue(vm.state.value.isHoldSpeedActive)
        // A speed picked while held is stored but waits for the release.
        vm.onAction(PlayerAction.OnSpeedSelect(1.25f))
        assertEquals(2f, engine.speed)

        vm.onAction(PlayerAction.OnHoldSpeedChange(false))
        assertEquals(1.25f, engine.speed)
        assertFalse(vm.state.value.isHoldSpeedActive)
        assertEquals(1.25f, vm.state.value.playbackSpeed)
    }

    @Test
    fun holdIsIgnoredWhileLockedOrPausedAndReleaseWithoutHoldDoesNothing() = playerTest {
        val engine = start()
        vm.onAction(PlayerAction.OnLockChange(true))
        vm.onAction(PlayerAction.OnHoldSpeedChange(true))
        assertFalse(vm.state.value.isHoldSpeedActive)

        vm.onAction(PlayerAction.OnLockChange(false))
        vm.onAction(PlayerAction.Pause)
        runCurrent()
        vm.onAction(PlayerAction.OnHoldSpeedChange(true))
        assertFalse(vm.state.value.isHoldSpeedActive)

        val before = engine.speedChanges
        vm.onAction(PlayerAction.OnHoldSpeedChange(false))
        assertEquals(before, engine.speedChanges)
    }

    @Test
    fun positionTicksStayOutOfTheMainState() = playerTest {
        val engine = start()
        val stateBefore = vm.state.value

        engine.position = 4_000
        advanceTimeBy(501)
        runCurrent()

        assertEquals(4_000L, vm.positionMs.value)
        assertSame(stateBefore, vm.state.value)
    }

    @Test
    fun hiddenChaptersStillGiveTheSkipIntroButton() = playerTest {
        val engine = start(settings = FakeSettingsRepository().apply { showChapters.value = false })
        val chapters =
            listOf(PlaybackChapter(0, 90_000, "Opening"), PlaybackChapter(90_000, null, "A"))

        engine.position = 10_000
        engine.emit {
            it.copy(
                chapters = chapters,
                durationMs = 1_400_000,
                isSeekable = true,
                playbackState = EnginePlaybackState.READY,
            )
        }
        advanceTimeBy(1_000)
        runCurrent()

        assertFalse(vm.state.value.showChapters)
        assertEquals(chapters, vm.state.value.chapters)
        assertEquals(SkipSegmentType.INTRO, vm.state.value.skipButton)
    }

    @Test
    fun chaptersReachTheState() = playerTest {
        val engine = start()
        val chapters =
            listOf(PlaybackChapter(0, 90_000, "Opening"), PlaybackChapter(90_000, null, "A"))

        engine.emit { it.copy(chapters = chapters) }
        runCurrent()

        assertEquals(chapters, vm.state.value.chapters)
    }

    @Test
    fun animeDefaultsToJapaneseAudioAndAManualSubtitleChoiceIsRemembered() = playerTest {
        val engine = start(MediaType.ANIME)
        engine.discoverTracks()
        runCurrent()
        assertEquals("japanese", engine.selectedAudio)
        assertEquals("full", engine.selectedSubtitle)

        vm.onAction(PlayerAction.SelectSubtitle("signs"))
        runCurrent()
        assertEquals("signs", engine.selectedSubtitle)
        assertEquals("en · Signs & Songs", preferences.saved[1L]?.subtitle?.label)

        // Later discovery keeps the manual choice.
        engine.discoverTracks()
        runCurrent()
        assertEquals("signs", engine.selectedSubtitle)
    }

    @Test
    fun completionIsSavedBeforeTheNextEpisodeOpens() = playerTest {
        val engine = start()
        engine.position = 1_000

        engine.finish()
        runCurrent()

        assertTrue(repository.saved.first().ended)
        assertEquals(11L, repository.saved.first().episodeId)
        assertEquals(2L, vm.state.value.session)
        assertTrue(engine.released)
    }

    @Test
    fun untouchedFileSavesNothingOnPause() = playerTest {
        start()

        vm.onAction(PlayerAction.Pause)

        assertTrue(repository.saved.isEmpty())
    }
}

private class PlaybackFake : PlaybackRepository {
    var type = MediaType.TV_SHOW
    val saved = mutableListOf<PlaybackSnapshot>()

    private fun first() =
        PlaybackTarget(
            1,
            11,
            type,
            "Show",
            "content://fixture/one",
            null,
            "EXOPLAYER",
            season = 1,
            episode = 1,
        )

    override suspend fun target(type: MediaType, id: Long) = first()

    override suspend fun adjacent(target: PlaybackTarget, next: Boolean) =
        if (next && target.episodeId == 11L)
            first().copy(episodeId = 12, filePath = "content://fixture/two", episode = 2)
        else null

    override suspend fun progress(target: PlaybackTarget): WatchProgress? = null

    override fun save(snapshot: PlaybackSnapshot): Deferred<OperationResult<Unit, DataError>> {
        saved += snapshot
        return CompletableDeferred(OperationResult.Success(Unit))
    }
}

private class EngineFactoryFake : PlayerEngineFactory {
    val engines = mutableListOf<EngineFake>()

    override suspend fun create(type: EngineType): LionPlayerEngine =
        EngineFake(type).also { engines += it }
}

private class EngineFake(override val engineType: EngineType) : LionPlayerEngine {
    private val mutable = MutableStateFlow(EngineState())
    override val state = mutable.asStateFlow()
    var position = 0L
    var released = false
    var speed = 1f
    var speedChanges = 0
    var selectedAudio: String? = null
    var selectedSubtitle: String? = null
    private var audio = emptyList<EngineTrackInfo>()
    private var subtitles = emptyList<EngineTrackInfo>()

    override val currentPositionMs
        get() = position

    override val durationMs = 1_000L

    fun emit(change: (EngineState) -> EngineState) {
        mutable.value = change(mutable.value)
    }

    override suspend fun setMedia(uri: Uri, subtitleUri: Uri?) {
        position = 0
        mutable.value = EngineState(playbackState = EnginePlaybackState.READY, durationMs = 1_000)
    }

    override fun play() = emit { it.copy(isPlaying = true) }

    override fun pause() = emit { it.copy(isPlaying = false) }

    override fun stop() {
        mutable.value = EngineState()
    }

    override fun release() {
        released = true
    }

    override fun seekTo(positionMs: Long) {
        position = positionMs
    }

    override fun setPlaybackSpeed(speed: Float) {
        this.speed = speed
        speedChanges++
    }

    fun discoverTracks() {
        audio =
            listOf(
                EngineTrackInfo("japanese", "ja", false),
                EngineTrackInfo("english", "en", false),
            )
        subtitles =
            listOf(
                EngineTrackInfo("signs", "en · Signs & Songs", false),
                EngineTrackInfo("full", "en · Full dialogue", false),
            )
        emit { it.copy(trackRevision = it.trackRevision + 1) }
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

    fun finish() = emit { it.copy(isPlaying = false, playbackState = EnginePlaybackState.ENDED) }
}

private class TrackPreferencesFake : TrackPreferenceRepository {
    val saved = mutableMapOf<Long, TrackPreference>()

    override suspend fun get(mediaId: Long) = saved[mediaId]

    override suspend fun save(mediaId: Long, preference: TrackPreference) {
        saved[mediaId] = preference
    }
}
