package com.singam.lionlibrary.testing

import android.content.Intent
import android.net.Uri
import com.singam.lionlibrary.domain.model.DataError
import com.singam.lionlibrary.domain.model.Episode
import com.singam.lionlibrary.domain.model.JumpBackInItem
import com.singam.lionlibrary.domain.model.LibraryQuery
import com.singam.lionlibrary.domain.model.MediaFilter
import com.singam.lionlibrary.domain.model.MediaItem
import com.singam.lionlibrary.domain.model.MediaType
import com.singam.lionlibrary.domain.model.OperationResult
import com.singam.lionlibrary.domain.model.ScanSummary
import com.singam.lionlibrary.domain.model.Season
import com.singam.lionlibrary.domain.model.WatchProgress
import com.singam.lionlibrary.domain.repository.DeletionError
import com.singam.lionlibrary.domain.repository.DeletionOutcome
import com.singam.lionlibrary.domain.repository.DeletionPreview
import com.singam.lionlibrary.domain.repository.DeletionTarget
import com.singam.lionlibrary.domain.repository.LetterboxdRepository
import com.singam.lionlibrary.domain.repository.MediaDeletionRepository
import com.singam.lionlibrary.domain.repository.MediaRepository
import com.singam.lionlibrary.domain.repository.MetadataRepository
import com.singam.lionlibrary.domain.repository.SettingsRepository
import com.singam.lionlibrary.domain.repository.WatchProgressRepository
import com.singam.lionlibrary.domain.usecase.LaunchPlayerUseCase
import com.singam.lionlibrary.domain.usecase.ScanCoordinator
import com.singam.lionlibrary.domain.usecase.ScanEvent
import com.singam.lionlibrary.domain.usecase.ScanRunState
import com.singam.lionlibrary.domain.usecase.ScanTrigger
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update

/** In-memory test fixtures shared by ViewModel tests. No I/O, no Android framework calls. */
fun media(
    id: Long,
    type: MediaType = MediaType.MOVIE,
    title: String = "Title $id",
    genres: String? = null,
    filePath: String? = if (type == MediaType.MOVIE) "content://fixture/$id.mkv" else null,
    tmdbId: Int? = id.toInt(),
    isWatchlisted: Boolean = false,
    isUnidentified: Boolean = false,
) =
    MediaItem(
        id = id,
        tmdbId = tmdbId,
        title = title,
        originalTitle = null,
        overview = null,
        posterPath = null,
        backdropPath = null,
        genres = genres,
        rating = null,
        year = 2024,
        mediaType = type,
        matchConfidence = 1f,
        isUnidentified = isUnidentified,
        lastUpdated = 1,
        filePath = filePath,
        isWatchlisted = isWatchlisted,
    )

fun episode(id: Long, showId: Long, season: Int, number: Int, end: Int? = null) =
    Episode(
        id = id,
        showId = showId,
        seasonNumber = season,
        episodeNumber = number,
        title = "E$number",
        overview = null,
        runtime = null,
        airDate = null,
        thumbnailPath = null,
        filePath = "content://fixture/$showId/$id.mkv",
        episodeEndNumber = end,
    )

/** Counts live collectors so tests can prove a screen stops observing Room while hidden. */
class SubscriptionCounter {
    val active = MutableStateFlow(0)
    var starts = 0
        private set

    fun <T> track(source: Flow<T>): Flow<T> =
        source
            .onStart {
                starts++
                active.update { it + 1 }
            }
            .onCompletion { active.update { it - 1 } }
}

class FakeMediaRepository : MediaRepository {
    val items = MutableStateFlow<Map<Long, MediaItem>>(emptyMap())
    val seasons = MutableStateFlow<Map<Long, List<Season>>>(emptyMap())
    val episodes = MutableStateFlow<Map<Long, List<Episode>>>(emptyMap())
    var byType: Map<MediaType, List<MediaItem>> = emptyMap()
    var browseAnswer: (LibraryQuery) -> List<MediaItem> = { emptyList() }
    var browseFailure: Throwable? = null
    val queries = mutableListOf<LibraryQuery>()
    val cancelled = mutableListOf<String>()
    val subscriptions = SubscriptionCounter()
    val toggled = mutableListOf<Long>()

    fun put(vararg values: MediaItem) = items.update { it + values.associateBy { item -> item.id } }

    override fun browse(query: LibraryQuery): Flow<List<MediaItem>> =
        subscriptions.track(
            flow {
                queries += query
                try {
                    browseFailure?.let { throw it }
                    emit(browseAnswer(query))
                    awaitCancellation()
                } finally {
                    cancelled += query.text
                }
            }
        )

    override suspend fun toggleWatchlist(id: Long) {
        toggled += id
        items.update { all ->
            all[id]?.let { all + (id to it.copy(isWatchlisted = !it.isWatchlisted)) } ?: all
        }
    }

    val byTypeFlow = MutableStateFlow<Map<MediaType, List<MediaItem>>>(emptyMap())
    var typeFailure: Throwable? = null

    override fun getByType(type: MediaType): Flow<List<MediaItem>> =
        subscriptions.track(
            flow {
                typeFailure?.let { throw it }
                emitAll(byTypeFlow.map { it[type] ?: byType[type].orEmpty() })
            }
        )

    override fun search(query: String, filter: MediaFilter) =
        browse(LibraryQuery(text = query, type = filter))

    override fun getRecentlyAdded(): Flow<List<MediaItem>> =
        subscriptions.track(MutableStateFlow(emptyList()))

    override suspend fun getById(id: Long): MediaItem? = items.value[id]

    override fun observeById(id: Long): Flow<MediaItem?> = subscriptions.track(items.map { it[id] })

    override fun getSeasonsForShow(showId: Long): Flow<List<Season>> =
        subscriptions.track(seasons.map { it[showId].orEmpty() })

    override fun getEpisodesForSeason(showId: Long, seasonNumber: Int): Flow<List<Episode>> =
        subscriptions.track(
            episodes.map { all -> all[showId].orEmpty().filter { it.seasonNumber == seasonNumber } }
        )

    override fun getAllEpisodesForShow(showId: Long): Flow<List<Episode>> =
        subscriptions.track(
            episodes.map { all ->
                all[showId]
                    .orEmpty()
                    .sortedWith(compareBy({ it.seasonNumber }, { it.episodeNumber }))
            }
        )

    override suspend fun getEpisodeById(episodeId: Long): Episode? =
        episodes.value.values.flatten().find { it.id == episodeId }
}

class FakeWatchProgressRepository : WatchProgressRepository {
    val progress = MutableStateFlow<Map<Pair<Long, Long>, WatchProgress>>(emptyMap())
    val jumpBackIn = MutableStateFlow<List<JumpBackInItem>>(emptyList())
    val calls = mutableListOf<String>()
    val subscriptions = SubscriptionCounter()
    private var clock = 0L

    override fun getJumpBackInItems(): Flow<List<JumpBackInItem>> = subscriptions.track(jumpBackIn)

    override suspend fun getProgress(mediaId: Long, episodeId: Long) =
        progress.value[mediaId to episodeId]

    override fun getProgressForMedia(mediaId: Long): Flow<List<WatchProgress>> =
        subscriptions.track(progress.map { all -> all.values.filter { it.mediaId == mediaId } })

    private fun write(mediaId: Long, episodeId: Long, ratio: Float, done: Boolean, pos: Long = 0) {
        progress.update {
            it +
                ((mediaId to episodeId) to
                    WatchProgress(mediaId, episodeId, ratio, pos, 1000, ++clock, done))
        }
    }

    fun seed(mediaId: Long, episodeId: Long, positionMs: Long, completed: Boolean = false) =
        write(mediaId, episodeId, if (completed) 1f else 0.5f, completed, positionMs)

    override suspend fun markAsStarted(mediaId: Long, episodeId: Long) {
        calls += "started:$mediaId:$episodeId"
    }

    override suspend fun markAsWatched(mediaId: Long, episodeId: Long) {
        calls += "watched:$mediaId:$episodeId"
        write(mediaId, episodeId, 1f, true)
    }

    override suspend fun markAllAsWatched(mediaId: Long, episodeIds: List<Long>) {
        calls += "watchedAll:$mediaId:${episodeIds.joinToString(",")}"
        episodeIds.forEach { write(mediaId, it, 1f, true) }
    }

    override suspend fun markAsUnwatched(mediaId: Long, episodeId: Long) {
        calls += "unwatched:$mediaId:$episodeId"
        write(mediaId, episodeId, 0f, false)
    }

    override suspend fun deleteProgress(mediaId: Long, episodeId: Long) {
        calls += "delete:$mediaId:$episodeId"
        progress.update { it - (mediaId to episodeId) }
    }

    override suspend fun deleteProgressForMedia(mediaId: Long) {
        calls += "deleteAll:$mediaId"
    }

    override suspend fun hideMediaFromJumpBackIn(mediaId: Long) {
        calls += "hide:$mediaId"
    }

    var clearFailure: Throwable? = null

    override suspend fun clearAll() {
        clearFailure?.let { throw it }
        calls += "clear"
        progress.value = emptyMap()
    }
}

class FakeSettingsRepository : SettingsRepository {
    override val keyVerified = MutableStateFlow(false)
    override val setupDismissed = MutableStateFlow(false)
    override val scanSummary = MutableStateFlow(ScanSummary())
    override val tmdbApiKey = MutableStateFlow("")
    override val moviesFolderUri = MutableStateFlow("")
    override val showsFolderUri = MutableStateFlow("")
    override val lastScanTime = MutableStateFlow(0L)
    override val forceLibVlc = MutableStateFlow(false)
    override val forceExternalPlayer = MutableStateFlow(false)
    override val onlineSkipTimes = MutableStateFlow(true)

    /** When set, every write throws it (a full or unreadable DataStore). */
    var writeFailure: Throwable? = null

    private fun <T> write(target: MutableStateFlow<T>, value: T) {
        writeFailure?.let { throw it }
        target.value = value
    }

    override suspend fun setKeyVerified(verified: Boolean) = write(keyVerified, verified)

    override suspend fun setSetupDismissed(dismissed: Boolean) = write(setupDismissed, dismissed)

    override suspend fun setScanSummary(summary: ScanSummary) = write(scanSummary, summary)

    override suspend fun setTmdbApiKey(key: String) {
        write(tmdbApiKey, key)
        keyVerified.value = false
    }

    override suspend fun setMoviesFolderUri(uri: String) = write(moviesFolderUri, uri)

    override suspend fun setShowsFolderUri(uri: String) = write(showsFolderUri, uri)

    override suspend fun setLastScanTime(time: Long) = write(lastScanTime, time)

    override suspend fun setForceLibVlc(enabled: Boolean) = write(forceLibVlc, enabled)

    override suspend fun setForceExternalPlayer(enabled: Boolean) =
        write(forceExternalPlayer, enabled)

    override suspend fun setOnlineSkipTimes(enabled: Boolean) = write(onlineSkipTimes, enabled)
}

class FakeMetadataRepository : MetadataRepository {
    var validation: OperationResult<Unit, DataError> = OperationResult.Success(Unit)
    val validated = mutableListOf<String>()
    /** When set, validation suspends until it completes (an in-flight network check). */
    var gate: kotlinx.coroutines.CompletableDeferred<Unit>? = null

    override suspend fun refreshArtwork(): OperationResult<Int, DataError> =
        OperationResult.Success(0)

    override suspend fun validateKey(key: String): OperationResult<Unit, DataError> {
        validated += key
        gate?.await()
        return validation
    }
}

class FakeScanCoordinator : ScanCoordinator {
    override val state = MutableStateFlow(ScanRunState())
    override val events = MutableSharedFlow<ScanEvent>()
    val started = mutableListOf<ScanTrigger>()
    var cancels = 0

    override fun startScan(trigger: ScanTrigger): Boolean {
        started += trigger
        return true
    }

    override fun autoScanOnLaunch() {}

    override fun cancelScan() {
        cancels++
    }
}

class FakeLetterboxdRepository : LetterboxdRepository {
    val refreshed = mutableListOf<Long>()
    var answer: (Long) -> MediaItem? = { null }

    override suspend fun refreshRating(mediaId: Long): MediaItem? {
        refreshed += mediaId
        return answer(mediaId)
    }

    override fun filmUrl(tmdbId: Int) = "https://letterboxd.example/tmdb/$tmdbId/"
}

class FakeDeletionRepository : MediaDeletionRepository {
    override suspend fun preview(
        target: DeletionTarget
    ): OperationResult<DeletionPreview, DeletionError> =
        OperationResult.Failure(DeletionError.UNAVAILABLE)

    override suspend fun delete(
        preview: DeletionPreview
    ): OperationResult<DeletionOutcome, DeletionError> =
        OperationResult.Failure(DeletionError.UNAVAILABLE)
}

/** External intents need the Android framework; JVM tests never reach this path. */
class UnusedLaunchPlayer : LaunchPlayerUseCase {
    override fun invoke(fileUri: Uri, positionMs: Long): Intent = error("not used in JVM tests")
}
