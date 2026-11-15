package com.singam.lionlibrary.data.scanner

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.singam.lionlibrary.data.local.db.dao.EpisodeDao
import com.singam.lionlibrary.data.local.db.dao.MediaDao
import com.singam.lionlibrary.data.local.db.dao.SeasonDao
import com.singam.lionlibrary.data.local.db.entity.EpisodeEntity
import com.singam.lionlibrary.data.local.db.entity.MediaEntity
import com.singam.lionlibrary.data.local.db.entity.SeasonEntity
import com.singam.lionlibrary.data.mapper.inferMediaType
import com.singam.lionlibrary.data.mapper.toMediaEntity
import com.singam.lionlibrary.data.remote.TmdbRetryPolicy
import com.singam.lionlibrary.data.remote.api.TmdbApiService
import com.singam.lionlibrary.data.remote.dto.SeasonDetailsDto
import com.singam.lionlibrary.data.remote.retryTmdb
import com.singam.lionlibrary.domain.model.EngineType
import com.singam.lionlibrary.domain.model.MediaType
import com.singam.lionlibrary.domain.model.ScanProgress
import com.singam.lionlibrary.domain.model.ScanStatus
import com.singam.lionlibrary.domain.repository.SettingsRepository
import com.singam.lionlibrary.domain.usecase.ScanLibraryUseCase
import com.singam.lionlibrary.util.Constants
import com.singam.lionlibrary.util.KeyedMutex
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.withLock

private class ScanAbortedException : RuntimeException()

class AndroidMediaScanner(
    private val context: Context,
    private val folderScanner: FolderScanner,
    private val fileNameParser: FileNameParser,
    private val tmdbApiService: TmdbApiService,
    private val imageCacheManager: ImageCacheManager,
    private val mediaDao: MediaDao,
    private val seasonDao: SeasonDao,
    private val episodeDao: EpisodeDao,
    private val settingsRepository: SettingsRepository,
    private val database: com.singam.lionlibrary.data.local.db.MediaDatabase,
    private val reconciler: com.singam.lionlibrary.data.storage.LibraryReconciler,
    private val mutationGate: com.singam.lionlibrary.data.storage.LibraryMutationGate,
) : ScanLibraryUseCase {

    private val matcher = TmdbMatcher(tmdbApiService)
    // Entries disappear when released, so nothing accumulates across scans.
    private val showLocks = KeyedMutex<Int>()
    private val movieLocks = KeyedMutex<Int>()
    private val seasonLocks = KeyedMutex<Pair<Long, Int>>()

    private suspend fun <T> withRetry(block: suspend () -> T): T =
        retryTmdb(TmdbRetryPolicy.Details, block = block)

    private companion object {
        /** Completed media/episode rows written together. */
        const val BATCH_SIZE = 20
    }

    private sealed interface FileResult {
        data class Media(val entity: MediaEntity) : FileResult

        data class Episode(val entity: EpisodeEntity) : FileResult

        data class Skipped(val displayName: String) : FileResult

        data class Error(val displayName: String, val status: ScanStatus) : FileResult

        data class FatalAbort(val status: ScanStatus, val displayName: String) : FileResult
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override operator fun invoke(): Flow<ScanProgress> = flow {
        mutationGate.mutex.withLock {
            reconciler.reconcile()
            reconciler.mergeEpisodeDuplicates()
            emitAll(scan())
        }
    }
        .flowOn(Dispatchers.IO)

    /** Phases, in order: settings check, folder discovery, library index, candidates, finish. */
    private fun scan(): Flow<ScanProgress> = flow {
        val apiKey = settingsRepository.tmdbApiKey.first()
        if (apiKey.isBlank()) {
            emit(ScanProgress(0, 0, "", ScanStatus.API_KEY_MISSING))
            return@flow
        }

        val candidates =
            when (val discovery = discoverCandidates()) {
                is Discovery.Failed -> {
                    emit(ScanProgress(0, 0, "", discovery.status))
                    return@flow
                }
                is Discovery.Found -> discovery.candidates
            }

        val total = countFiles(candidates)
        if (total == 0) {
            emit(ScanProgress(0, 0, "", ScanStatus.COMPLETE))
            return@flow
        }
        emit(ScanProgress(total, 0, "", ScanStatus.SCANNING))

        val index = loadIndex()
        val aborted = processCandidates(candidates, apiKey, index, total)
        if (aborted) return@flow

        settingsRepository.setLastScanTime(System.currentTimeMillis())
        emit(ScanProgress(total, total, "", ScanStatus.COMPLETE))
    }
        .flowOn(Dispatchers.IO)

    private sealed interface Discovery {
        data class Found(val candidates: List<MediaCandidate>) : Discovery

        data class Failed(val status: ScanStatus) : Discovery
    }

    /** Lists both roots. Any unreadable folder fails the whole scan before a single write. */
    private suspend fun discoverCandidates(): Discovery {
        val moviesFolderUri = settingsRepository.moviesFolderUri.first()
        val showsFolderUri = settingsRepository.showsFolderUri.first()
        val candidates = mutableListOf<MediaCandidate>()
        try {
            if (moviesFolderUri.isNotBlank()) {
                candidates +=
                    folderScanner.scanMoviesFolder(
                        Uri.parse(moviesFolderUri),
                        showsFolderUri.takeIf { it.isNotBlank() }?.let { Uri.parse(it) },
                    )
            }
            if (showsFolderUri.isNotBlank()) {
                candidates += folderScanner.scanShowsFolder(Uri.parse(showsFolderUri))
            }
        } catch (e: FolderPermissionException) {
            return Discovery.Failed(ScanStatus.PERMISSION_REVOKED)
        } catch (e: FolderUnavailableException) {
            // A root or subfolder could not be listed completely. Report it like lost access:
            // an unreadable folder must never finish as an empty, successful scan.
            return Discovery.Failed(ScanStatus.PERMISSION_REVOKED)
        }
        return Discovery.Found(candidates)
    }

    private fun countFiles(candidates: List<MediaCandidate>): Int = candidates.sumOf {
        when (it) {
            is MediaCandidate.Movie -> 1
            is MediaCandidate.Show -> it.seasons.values.sumOf { eps -> eps.size }
            is MediaCandidate.Unknown -> 1
        }
    }

    /** One read of each table instead of several queries per file. */
    private suspend fun loadIndex(): ScanIndex =
        ScanIndex(mediaDao.getAll().first(), episodeDao.getAll())

    /**
     * Runs candidates with bounded concurrency and writes results in batches. Returns true when a
     * fatal status (invalid key, no connection) stopped the scan; completed writes are flushed in
     * every case, including cancellation.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private suspend fun FlowCollector<ScanProgress>.processCandidates(
        candidates: List<MediaCandidate>,
        apiKey: String,
        index: ScanIndex,
        total: Int,
    ): Boolean {
        val processedCount = AtomicInteger(0)
        val writer = BatchWriter()
        var abortStatus: ScanStatus? = null
        try {
            candidates
                .asFlow()
                .flatMapMerge(concurrency = Constants.SCAN_CONCURRENCY) { candidate ->
                    candidateResults(candidate, apiKey, index)
                }
                .collect { result ->
                    when (result) {
                        is FileResult.Skipped -> {
                            val count = processedCount.incrementAndGet()
                            emit(ScanProgress(total, count, result.displayName, ScanStatus.SKIPPED))
                        }
                        is FileResult.Media -> {
                            val count = processedCount.incrementAndGet()
                            emit(
                                ScanProgress(total, count, result.entity.title, ScanStatus.MATCHED)
                            )
                            writer.add(result.entity)
                        }
                        is FileResult.Episode -> {
                            val count = processedCount.incrementAndGet()
                            emit(ScanProgress(total, count, "", ScanStatus.MATCHED))
                            writer.add(result.entity)
                        }
                        is FileResult.Error -> {
                            val count = processedCount.incrementAndGet()
                            emit(ScanProgress(total, count, result.displayName, result.status))
                        }
                        is FileResult.FatalAbort -> {
                            writer.flush()
                            abortStatus = result.status
                            emit(
                                ScanProgress(
                                    total,
                                    processedCount.get(),
                                    result.displayName,
                                    result.status,
                                )
                            )
                            throw ScanAbortedException()
                        }
                    }
                }
        } catch (_: ScanAbortedException) {
            // A fatal status has already been emitted; cancel remaining candidate work.
        } finally {
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { writer.flush() }
        }
        return abortStatus != null
    }

    /** Completed rows, written every [BATCH_SIZE] results and when the scan stops. */
    private inner class BatchWriter {
        private val mediaBatch = mutableListOf<MediaEntity>()
        private val episodeBatch = mutableListOf<EpisodeEntity>()

        suspend fun add(entity: MediaEntity) {
            mediaBatch.add(entity)
            if (mediaBatch.size >= BATCH_SIZE) flushMedia()
        }

        suspend fun add(entity: EpisodeEntity) {
            episodeBatch.add(entity)
            if (episodeBatch.size >= BATCH_SIZE) flushEpisodes()
        }

        suspend fun flush() {
            flushMedia()
            flushEpisodes()
        }

        private suspend fun flushMedia() {
            if (mediaBatch.isEmpty()) return
            mediaDao.insertAll(mediaBatch.toList())
            mediaBatch.clear()
        }

        /**
         * Inserts the episodes and folds any raw "local file" row for the same file into them,
         * moving its history. The raw rows are read inside the transaction (one query per batch):
         * they may have been written earlier in this scan, so the start-of-scan index is not used.
         */
        private suspend fun flushEpisodes() {
            if (episodeBatch.isEmpty()) return
            database.withTransaction {
                val pendingByPath =
                    mediaDao
                        .getUnidentifiedByFilePaths(episodeBatch.map { it.filePath })
                        .associateBy {
                            it.filePath
                        }
                for (episode in episodeBatch) {
                    val id = episodeDao.insert(episode)
                    val pending = pendingByPath[episode.filePath] ?: continue
                    database.watchProgressDao().getProgress(pending.id, 0L)?.let { progress ->
                        database
                            .watchProgressDao()
                            .upsert(progress.copy(mediaId = episode.showId, episodeId = id))
                    }
                    database.watchProgressDao().deleteProgressForMedia(pending.id)
                    mediaDao.deleteById(pending.id)
                }
            }
            episodeBatch.clear()
        }
    }

    /** One candidate's results, with the documented failure mapping for each error type. */
    private fun candidateResults(
        candidate: MediaCandidate,
        apiKey: String,
        index: ScanIndex,
    ): Flow<FileResult> = flow {
        try {
            when (candidate) {
                is MediaCandidate.Movie -> emit(movieResult(candidate, apiKey, index))
                is MediaCandidate.Show -> emitAll(showResults(candidate, apiKey, index))
                is MediaCandidate.Unknown -> {
                    handleUnidentified(
                        candidate.rawName,
                        candidate.sourceUri.toString(),
                        candidate.expectedType,
                    )
                    emit(FileResult.Error(candidate.rawName, ScanStatus.UNIDENTIFIED))
                }
            }
        } catch (e: retrofit2.HttpException) {
            if (e.code() == 401) {
                emit(FileResult.FatalAbort(ScanStatus.INVALID_API_KEY, getCandidateName(candidate)))
            } else {
                handleCandidateError(candidate)
                emit(FileResult.Error(getCandidateName(candidate), ScanStatus.ERROR))
            }
        } catch (e: java.io.IOException) {
            handleCandidateError(candidate)
            emit(FileResult.FatalAbort(ScanStatus.NO_INTERNET, getCandidateName(candidate)))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            handleCandidateError(candidate)
            emit(FileResult.Error(getCandidateName(candidate), ScanStatus.ERROR))
        }
    }

    /** Identified movies are skipped (only a missing sidecar is filled in); others are matched. */
    private suspend fun movieResult(
        candidate: MediaCandidate.Movie,
        apiKey: String,
        index: ScanIndex,
    ): FileResult {
        val fileUriString = candidate.sourceUri.toString()
        val existingMedia = index.media(fileUriString)
        if (existingMedia != null && !existingMedia.isUnidentified) {
            index.mediaSubtitleBackfill(fileUriString, candidate.subtitleUri?.toString())?.let {
                mediaDao.fillMissingSubtitle(fileUriString, it)
            }
            return FileResult.Skipped(candidate.title)
        }
        val entity =
            processMovie(candidate, fileUriString, apiKey)?.let { matched ->
                if (existingMedia == null) matched
                else
                    matched.copy(
                        id = existingMedia.id,
                        addedAt = existingMedia.addedAt,
                        isWatchlisted = existingMedia.isWatchlisted,
                        preferredEngine = existingMedia.preferredEngine,
                        externalSubtitlePath =
                            matched.externalSubtitlePath ?: existingMedia.externalSubtitlePath,
                    )
            }
        return if (entity != null) FileResult.Media(entity)
        else FileResult.Error(candidate.title, ScanStatus.UNIDENTIFIED)
    }

    /**
     * Back-fills ranges/sidecars of known episode files, then skips a fully known show without any
     * TMDB lookup (this matters for the auto-scan on every launch).
     */
    private suspend fun showResults(
        candidate: MediaCandidate.Show,
        apiKey: String,
        index: ScanIndex,
    ): Flow<FileResult> {
        val episodeFiles = candidate.seasons.values.flatten()
        episodeFiles.forEach { file ->
            val path = file.uri.toString()
            val backfill =
                index.episodeBackfill(path, file.episodeEndNumber, file.subtitleUri?.toString())
                    ?: return@forEach
            backfill.rangeEnd?.let { episodeDao.updateRange(path, it) }
            backfill.subtitle?.let { episodeDao.fillMissingSubtitle(path, it) }
        }
        val allKnown =
            episodeFiles.isNotEmpty() &&
                episodeFiles.all { index.isKnownEpisode(it.uri.toString()) }
        return if (allKnown)
            flow { repeat(episodeFiles.size) { emit(FileResult.Skipped(candidate.title)) } }
        else processShow(candidate, apiKey, index)
    }

    private fun getCandidateName(candidate: MediaCandidate): String {
        return when (candidate) {
            is MediaCandidate.Movie -> candidate.title
            is MediaCandidate.Show -> candidate.title
            is MediaCandidate.Unknown -> candidate.rawName
        }
    }

    private suspend fun handleCandidateError(candidate: MediaCandidate) {
        when (candidate) {
            is MediaCandidate.Movie ->
                handleUnidentified(
                    candidate.title,
                    candidate.sourceUri.toString(),
                    MediaType.MOVIE,
                    candidate.subtitleUri?.toString(),
                    parsedYear = candidate.year,
                )
            is MediaCandidate.Unknown ->
                handleUnidentified(
                    candidate.rawName,
                    candidate.sourceUri.toString(),
                    candidate.expectedType,
                )
            is MediaCandidate.Show -> {
                for ((season, eps) in candidate.seasons) {
                    for (ep in eps) {
                        handleUnidentified(
                            "${candidate.title} S?E${ep.episodeNumber}",
                            ep.uri.toString(),
                            MediaType.TV_SHOW,
                            ep.subtitleUri?.toString(),
                            sourceShowTitle = candidate.title,
                            sourceSeason = season,
                            sourceEpisode = ep.episodeNumber,
                            sourceEpisodeEnd = ep.episodeEndNumber,
                        )
                    }
                }
            }
        }
    }

    private suspend fun processMovie(
        parsed: MediaCandidate.Movie,
        fileUri: String,
        apiKey: String,
    ): MediaEntity? {
        val matched = matcher.movie(parsed.title, parsed.year, apiKey)
        if (matched == null) {
            handleUnidentified(
                parsed.title,
                fileUri,
                MediaType.MOVIE,
                parsed.subtitleUri?.toString(),
                parsedYear = parsed.year,
            )
            return null
        }
        val firstResult = matched.candidate
        val confidence = matched.confidence

        return movieLocks.withLock(firstResult.id) {
            val details = withRetry { tmdbApiService.getMovieDetails(firstResult.id, apiKey) }

            val posterPath =
                details.posterPath?.let { path ->
                    imageCacheManager.cachePoster(path, "movie_${details.id}_poster.jpg")
                }
            val backdropPath =
                details.backdropPath?.let { path ->
                    imageCacheManager.cacheBackdrop(path, "movie_${details.id}_backdrop.jpg")
                }

            val images =
                try {
                    withRetry { tmdbApiService.getMovieImages(details.id, apiKey) }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: retrofit2.HttpException) {
                    if (e.code() == 404) null else throw e
                } catch (e: java.io.IOException) {
                    throw e
                } catch (e: Exception) {
                    null
                }

            val logoInfo =
                images?.logos?.firstOrNull { it.iso6391 == "en" } ?: images?.logos?.firstOrNull()
            val logoLocalPath =
                logoInfo?.filePath?.let { path ->
                    imageCacheManager.cacheLogo(path, "movie_${details.id}_logo.png")
                }

            details
                .toMediaEntity(
                    mediaType = MediaType.MOVIE,
                    confidence = confidence,
                    posterLocalPath = posterPath,
                    backdropLocalPath = backdropPath,
                    filePath = fileUri,
                )
                .copy(
                    logoPath = logoLocalPath,
                    externalSubtitlePath = parsed.subtitleUri?.toString(),
                    preferredEngine = determineEngine(Uri.parse(fileUri)),
                )
        }
    }

    private fun processShow(
        parsed: MediaCandidate.Show,
        apiKey: String,
        index: ScanIndex,
    ): Flow<FileResult> = flow {
        val matched = matcher.show(parsed.title, apiKey)
        val firstResult = matched?.candidate
        val confidence = matched?.confidence ?: 0f

        if (firstResult == null || confidence < Constants.MATCH_CONFIDENCE_THRESHOLD) {
            // Unidentified show, skipping episodes
            for ((season, eps) in parsed.seasons) {
                for (ep in eps) {
                    handleUnidentified(
                        ep.extraTitle ?: "${parsed.title} S${season}E${ep.episodeNumber}",
                        ep.uri.toString(),
                        MediaType.TV_SHOW,
                        ep.subtitleUri?.toString(),
                        parsed.title,
                        season,
                        ep.episodeNumber,
                        ep.episodeEndNumber,
                    )
                    emit(
                        FileResult.Error(
                            "${parsed.title} S?E${ep.episodeNumber}",
                            ScanStatus.UNIDENTIFIED,
                        )
                    )
                }
            }
            return@flow
        }

        val showId = getOrCreateShow(firstResult.id, apiKey, confidence, parsed.title)

        for ((seasonNum, episodes) in parsed.seasons) {
            val (seasonId, seasonDetails) =
                getOrCreateSeason(showId, firstResult.id, seasonNum, apiKey)

            for (ep in episodes) {
                val fileUriString = ep.uri.toString()
                if (index.isKnownEpisode(fileUriString)) {
                    emit(FileResult.Skipped(parsed.title))
                    continue
                }

                // Extras have synthetic numbers; don't attach a real special's metadata.
                val episodeInfo =
                    if (ep.extraTitle != null) null
                    else seasonDetails?.episodes?.find { it.episodeNumber == ep.episodeNumber }

                val thumbnailPath =
                    episodeInfo?.stillPath?.let { path ->
                        imageCacheManager.cacheEpisodeStill(
                            path,
                            "tv_${firstResult.id}_s${seasonNum}e${ep.episodeNumber}_still.jpg",
                        )
                    }

                val entity =
                    EpisodeEntity(
                        showId = showId,
                        seasonNumber = seasonNum,
                        episodeNumber = ep.episodeNumber,
                        title = ep.extraTitle ?: episodeInfo?.name,
                        overview = episodeInfo?.overview,
                        runtime = episodeInfo?.runtime,
                        airDate = episodeInfo?.airDate,
                        thumbnailPath = thumbnailPath,
                        filePath = fileUriString,
                        externalSubtitlePath = ep.subtitleUri?.toString(),
                        preferredEngine = determineEngine(ep.uri),
                        episodeEndNumber = ep.episodeEndNumber,
                    )
                emit(FileResult.Episode(entity))
            }
        }
    }

    private suspend fun getOrCreateShow(
        tmdbId: Int,
        apiKey: String,
        confidence: Float,
        parsedTitle: String,
    ): Long {
        return showLocks.withLock(tmdbId) {
            val existingByTmdb = mediaDao.getTvByTmdbId(tmdbId)
            if (existingByTmdb != null) return@withLock existingByTmdb.id

            val details = withRetry { tmdbApiService.getTvDetails(tmdbId, apiKey) }
            val finalMediaType = details.inferMediaType()

            val posterPath =
                details.posterPath?.let { path ->
                    imageCacheManager.cachePoster(path, "tv_${details.id}_poster.jpg")
                }
            val backdropPath =
                details.backdropPath?.let { path ->
                    imageCacheManager.cacheBackdrop(path, "tv_${details.id}_backdrop.jpg")
                }

            val images =
                try {
                    withRetry { tmdbApiService.getTvImages(tmdbId, apiKey) }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: retrofit2.HttpException) {
                    if (e.code() == 404) null else throw e
                } catch (e: java.io.IOException) {
                    throw e
                } catch (e: Exception) {
                    null
                }

            val logoInfo =
                images?.logos?.firstOrNull { it.iso6391 == "en" } ?: images?.logos?.firstOrNull()
            val logoLocalPath =
                logoInfo?.filePath?.let { path ->
                    imageCacheManager.cacheLogo(path, "tv_${details.id}_logo.png")
                }

            val entity =
                details
                    .toMediaEntity(
                        mediaType = finalMediaType,
                        confidence = confidence,
                        posterLocalPath = posterPath,
                        backdropLocalPath = backdropPath,
                    )
                    .copy(logoPath = logoLocalPath)
            mediaDao.insert(entity)
        }
    }

    private suspend fun getOrCreateSeason(
        showId: Long,
        tmdbId: Int,
        seasonNumber: Int,
        apiKey: String,
    ): Pair<Long, SeasonDetailsDto?> {
        return seasonLocks.withLock(Pair(showId, seasonNumber)) {
            val existing = seasonDao.getByShowAndSeason(showId, seasonNumber)

            val seasonDetails =
                try {
                    withRetry { tmdbApiService.getSeasonDetails(tmdbId, seasonNumber, apiKey) }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: retrofit2.HttpException) {
                    if (e.code() == 404) null else throw e
                } catch (e: java.io.IOException) {
                    throw e
                } catch (e: Exception) {
                    null
                }

            if (existing != null) return@withLock Pair(existing.id, seasonDetails)

            val seasonEntity =
                SeasonEntity(
                    showId = showId,
                    seasonNumber = seasonNumber,
                    name =
                        seasonDetails?.name
                            ?: if (seasonNumber == 0) "Specials" else "Season $seasonNumber",
                    posterPath =
                        seasonDetails?.posterPath?.let { path ->
                            imageCacheManager.cachePoster(
                                path,
                                "tv_${tmdbId}_s${seasonNumber}_poster.jpg",
                            )
                        },
                )
            val seasonId = seasonDao.insert(seasonEntity)
            Pair(seasonId, seasonDetails)
        }
    }

    private suspend fun handleUnidentified(
        rawName: String,
        fileUri: String,
        mediaType: MediaType,
        subtitleUriString: String? = null,
        sourceShowTitle: String? = null,
        sourceSeason: Int? = null,
        sourceEpisode: Int? = null,
        sourceEpisodeEnd: Int? = null,
        parsedYear: Int? = null,
    ) {
        // A file already catalogued as an episode keeps that identity. A failed or
        // unconfident show lookup must not add a second, unidentified row for it.
        if (episodeDao.getByFilePath(fileUri) != null) return
        val existing = mediaDao.getByFilePath(fileUri)
        if (existing != null) {
            if (existing.isUnidentified)
                mediaDao.update(
                    existing.copy(
                        title = rawName,
                        year = parsedYear ?: existing.year,
                        mediaType = mediaType.name,
                        sourceShowTitle = sourceShowTitle ?: existing.sourceShowTitle,
                        sourceSeason = sourceSeason ?: existing.sourceSeason,
                        sourceEpisode = sourceEpisode ?: existing.sourceEpisode,
                        sourceEpisodeEnd = sourceEpisodeEnd ?: existing.sourceEpisodeEnd,
                        externalSubtitlePath = subtitleUriString ?: existing.externalSubtitlePath,
                    )
                )
            return
        }

        mediaDao.insert(
            MediaEntity(
                tmdbId = null,
                title = rawName,
                originalTitle = null,
                overview = null,
                posterPath = null,
                backdropPath = null,
                genres = null,
                rating = null,
                year = parsedYear,
                mediaType = mediaType.name,
                matchConfidence = 0f,
                isUnidentified = true,
                duration = null,
                certification = null,
                lastUpdated = System.currentTimeMillis(),
                filePath = fileUri,
                externalSubtitlePath = subtitleUriString,
                preferredEngine = determineEngine(Uri.parse(fileUri)),
                sourceShowTitle = sourceShowTitle,
                sourceSeason = sourceSeason,
                sourceEpisode = sourceEpisode,
                sourceEpisodeEnd = sourceEpisodeEnd,
                identificationFailure = "No confident metadata match",
            )
        )
    }

    // Probe codecs to pick engine.
    private suspend fun determineEngine(uri: Uri): String {
        return if (CodecCapabilityChecker.canHardwareDecode(context, uri)) {
            EngineType.EXOPLAYER.name
        } else {
            EngineType.LIBVLC.name
        }
    }
}
