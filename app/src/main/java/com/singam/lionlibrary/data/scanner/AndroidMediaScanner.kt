package com.singam.lionlibrary.data.scanner

import android.content.Context
import android.net.Uri
import com.singam.lionlibrary.data.local.db.dao.EpisodeDao
import com.singam.lionlibrary.data.local.db.dao.MediaDao
import com.singam.lionlibrary.data.local.db.dao.SeasonDao
import com.singam.lionlibrary.data.local.db.entity.EpisodeEntity
import com.singam.lionlibrary.data.local.db.entity.MediaEntity
import com.singam.lionlibrary.data.local.db.entity.SeasonEntity
import com.singam.lionlibrary.data.mapper.toMediaEntity
import com.singam.lionlibrary.data.mapper.inferMediaType
import com.singam.lionlibrary.data.remote.api.TmdbApiService
import com.singam.lionlibrary.data.remote.dto.SeasonDetailsDto
import com.singam.lionlibrary.domain.model.MediaType
import com.singam.lionlibrary.domain.model.ScanProgress
import com.singam.lionlibrary.domain.model.ScanStatus
import com.singam.lionlibrary.domain.repository.SettingsRepository
import com.singam.lionlibrary.domain.usecase.ScanLibraryUseCase
import com.singam.lionlibrary.presentation.player.engine.EngineType
import com.singam.lionlibrary.util.ConfidenceScorer
import com.singam.lionlibrary.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.delay
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class AndroidMediaScanner(
    private val context: Context,
    private val folderScanner: FolderScanner,
    private val fileNameParser: FileNameParser,
    private val tmdbApiService: TmdbApiService,
    private val imageCacheManager: ImageCacheManager,
    private val mediaDao: MediaDao,
    private val seasonDao: SeasonDao,
    private val episodeDao: EpisodeDao,
    private val settingsRepository: SettingsRepository
) : ScanLibraryUseCase {

    private val showLocks = ConcurrentHashMap<Int, Mutex>()
    private val movieLocks = ConcurrentHashMap<Int, Mutex>()
    private val seasonLocks = ConcurrentHashMap<Pair<Long, Int>, Mutex>()
    

    private suspend fun <T> withRetry(times: Int = 5, block: suspend () -> T): T {
        var currentDelay = 2000L
        repeat(times - 1) {
            try {
                return block()
            } catch (e: retrofit2.HttpException) {
                if (e.code() == 429) {
                    delay(currentDelay)
                    currentDelay = (currentDelay * 1.5).toLong()
                } else if (e.code() >= 500) {
                    delay(currentDelay)
                    currentDelay *= 2
                } else throw e
            } catch (e: java.io.IOException) {
                delay(currentDelay)
                currentDelay *= 2
            }
        }
        return block()
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
        val apiKey = settingsRepository.tmdbApiKey.first()
        if (apiKey.isBlank()) {
            emit(ScanProgress(0, 0, "", ScanStatus.API_KEY_MISSING))
            return@flow
        }

        val moviesFolderUri = settingsRepository.moviesFolderUri.first()
        val showsFolderUri = settingsRepository.showsFolderUri.first()

        val allCandidates = mutableListOf<MediaCandidate>()

        try {
            if (moviesFolderUri.isNotBlank()) {
                allCandidates += folderScanner.scanMoviesFolder(Uri.parse(moviesFolderUri))
            }
            if (showsFolderUri.isNotBlank()) {
                allCandidates += folderScanner.scanShowsFolder(Uri.parse(showsFolderUri))
            }
        } catch (e: FolderPermissionException) {
            emit(ScanProgress(0, 0, "", ScanStatus.PERMISSION_REVOKED))
            return@flow
        }

        val total = allCandidates.sumOf { 
            when (it) {
                is MediaCandidate.Movie -> 1
                is MediaCandidate.Show -> it.seasons.values.sumOf { eps -> eps.size }
                is MediaCandidate.Unknown -> 1
            }
        }

        if (total == 0) {
            emit(ScanProgress(0, 0, "", ScanStatus.COMPLETE))
            return@flow
        }

        emit(ScanProgress(total, 0, "", ScanStatus.SCANNING))

        val processedCount = AtomicInteger(0)
        val mediaBatch = mutableListOf<MediaEntity>()
        val episodeBatch = mutableListOf<EpisodeEntity>()

        suspend fun flushMedia() {
            if (mediaBatch.isNotEmpty()) {
                mediaDao.insertAll(mediaBatch.toList())
                mediaBatch.clear()
            }
        }
        suspend fun flushEpisodes() {
            if (episodeBatch.isNotEmpty()) {
                episodeDao.insertAll(episodeBatch.toList())
                episodeBatch.clear()
            }
        }

        var abortStatus: ScanStatus? = null

        // Initialize the shared ExoPlayer probe instance on the main
        // thread (ExoPlayer requires main-thread construction), then
        // reuse it for every file in the scan session.
        kotlinx.coroutines.withContext(Dispatchers.Main) {
            CodecCapabilityChecker.initialize(context)
        }
        try {

        allCandidates
            .asFlow()
            .flatMapMerge(concurrency = Constants.SCAN_CONCURRENCY) { candidate ->
                flow {
                    try {
                        when (candidate) {
                            is MediaCandidate.Movie -> {
                                val fileUriString = candidate.sourceUri.toString()
                                val existingMedia = mediaDao.getByFilePath(fileUriString)
                                if (existingMedia != null) {
                                    emit(FileResult.Skipped(candidate.title))
                                } else {
                                    val entity = processMovie(candidate, fileUriString, apiKey)
                                    if (entity != null) emit(FileResult.Media(entity))
                                    else emit(FileResult.Error(candidate.title, ScanStatus.MATCHED))
                                }
                            }
                            is MediaCandidate.Show -> {
                                processShow(candidate, apiKey).collect { res ->
                                    emit(res)
                                }
                            }
                            is MediaCandidate.Unknown -> {
                                val fileUriString = candidate.sourceUri.toString()
                                handleUnidentified(candidate.rawName, fileUriString, candidate.expectedType)
                                emit(FileResult.Error(candidate.rawName, ScanStatus.MATCHED))
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
                    } catch (e: Exception) {
                        handleCandidateError(candidate)
                        emit(FileResult.Error(getCandidateName(candidate), ScanStatus.ERROR))
                    }
                }
            }
            .collect { result ->
                when (result) {
                    is FileResult.Skipped -> {
                        val count = processedCount.incrementAndGet()
                        emit(ScanProgress(total, count, result.displayName, ScanStatus.SKIPPED))
                    }
                    is FileResult.Media -> {
                        mediaBatch.add(result.entity)
                        val count = processedCount.incrementAndGet()
                        emit(ScanProgress(total, count, result.entity.title, ScanStatus.MATCHED))
                        if (mediaBatch.size >= 20) flushMedia()
                    }
                    is FileResult.Episode -> {
                        episodeBatch.add(result.entity)
                        val count = processedCount.incrementAndGet()
                        emit(ScanProgress(total, count, "", ScanStatus.MATCHED))
                        if (episodeBatch.size >= 20) flushEpisodes()
                    }
                    is FileResult.Error -> {
                        val count = processedCount.incrementAndGet()
                        emit(ScanProgress(total, count, result.displayName, result.status))
                    }
                    is FileResult.FatalAbort -> {
                        flushMedia()
                        flushEpisodes()
                        abortStatus = result.status
                        emit(ScanProgress(total, processedCount.get(), result.displayName, result.status))
                    }
                }
            }

        flushMedia()
        flushEpisodes()

        } finally {
            // Always release the probe player, even on error/cancellation.
            // Must run on main thread (ExoPlayer requirement).
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                CodecCapabilityChecker.shutdown()
            }
        }

        if (abortStatus != null) return@flow

        settingsRepository.setLastScanTime(System.currentTimeMillis())
        emit(ScanProgress(total, total, "", ScanStatus.COMPLETE))
    }.flowOn(Dispatchers.IO)

    private fun getCandidateName(candidate: MediaCandidate): String {
        return when (candidate) {
            is MediaCandidate.Movie -> candidate.title
            is MediaCandidate.Show -> candidate.title
            is MediaCandidate.Unknown -> candidate.rawName
        }
    }

    private suspend fun handleCandidateError(candidate: MediaCandidate) {
        when (candidate) {
            is MediaCandidate.Movie -> handleUnidentified(candidate.title, candidate.sourceUri.toString(), MediaType.MOVIE, candidate.subtitleUri?.toString())
            is MediaCandidate.Unknown -> handleUnidentified(candidate.rawName, candidate.sourceUri.toString(), candidate.expectedType)
            is MediaCandidate.Show -> {
                for (eps in candidate.seasons.values) {
                    for (ep in eps) {
                        handleUnidentified("${candidate.title} S?E${ep.episodeNumber}", ep.uri.toString(), MediaType.TV_SHOW, ep.subtitleUri?.toString())
                    }
                }
            }
        }
    }

    private suspend fun processMovie(
        parsed: MediaCandidate.Movie,
        fileUri: String,
        apiKey: String
    ): MediaEntity? {
        val searchResult = withRetry { tmdbApiService.searchMovie(apiKey, parsed.title, parsed.year) }
        var firstResult = searchResult.results.firstOrNull()
        
        var tmdbYear = firstResult?.releaseDate?.take(4)?.toIntOrNull()
        var confidence = if (firstResult != null) ConfidenceScorer.computeConfidence(
            parsed.title, firstResult.title, parsed.year, tmdbYear
        ) else 0f

        if (firstResult != null && confidence < Constants.MATCH_CONFIDENCE_THRESHOLD) {
            try {
                val altTitles = withRetry { tmdbApiService.getMovieAlternativeTitles(firstResult.id, apiKey) }
                val aliases = altTitles.titles ?: altTitles.results ?: emptyList()
                val hasMatch = aliases.any { alias ->
                    ConfidenceScorer.computeConfidence(parsed.title, alias.title, parsed.year, tmdbYear) >= Constants.MATCH_CONFIDENCE_THRESHOLD
                }
                if (hasMatch) {
                    confidence = 1.0f
                }
            } catch (e: Exception) {
                // Ignore failure and fallback to initial confidence
            }
        }

        if (firstResult == null || confidence < Constants.MATCH_CONFIDENCE_THRESHOLD) {
            handleUnidentified(parsed.title, fileUri, MediaType.MOVIE, parsed.subtitleUri?.toString())
            return null
        }

        val lock = movieLocks.computeIfAbsent(firstResult.id) { Mutex() }
        return lock.withLock {
            val existing = mediaDao.getByTmdbId(firstResult.id)
            if (existing != null) return@withLock null

            val details = withRetry { tmdbApiService.getMovieDetails(firstResult.id, apiKey) }

        val posterPath = details.posterPath?.let { path ->
            imageCacheManager.cachePoster(path, "movie_${details.id}_poster.jpg")
        }
        val backdropPath = details.backdropPath?.let { path ->
            imageCacheManager.cacheBackdrop(path, "movie_${details.id}_backdrop.jpg")
        }

        val images = try {
            withRetry { tmdbApiService.getMovieImages(details.id, apiKey) }
        } catch (e: Exception) {
            null
        }

        val logoInfo = images?.logos?.firstOrNull { it.iso6391 == "en" } ?: images?.logos?.firstOrNull()
        val logoLocalPath = logoInfo?.filePath?.let { path ->
            imageCacheManager.cacheLogo(path, "movie_${details.id}_logo.png")
        }

        return details.toMediaEntity(
            mediaType = MediaType.MOVIE,
            confidence = confidence,
            posterLocalPath = posterPath,
            backdropLocalPath = backdropPath,
            filePath = fileUri
        ).copy(
            logoPath = logoLocalPath,
            externalSubtitlePath = parsed.subtitleUri?.toString(),
            preferredEngine = determineEngine(Uri.parse(fileUri))
        )
        }
    }

    private suspend fun processShow(
        parsed: MediaCandidate.Show,
        apiKey: String
    ): Flow<FileResult> = flow {
        val searchResult = withRetry { tmdbApiService.searchTv(apiKey, parsed.title) }
        var firstResult = searchResult.results.firstOrNull()
        
        var tmdbYear = firstResult?.firstAirDate?.take(4)?.toIntOrNull()
        var confidence = if (firstResult != null) ConfidenceScorer.computeConfidence(
            parsed.title, firstResult.name, null, tmdbYear
        ) else 0f

        if (firstResult != null && confidence < Constants.MATCH_CONFIDENCE_THRESHOLD) {
            try {
                val altTitles = withRetry { tmdbApiService.getTvAlternativeTitles(firstResult.id, apiKey) }
                val aliases = altTitles.titles ?: altTitles.results ?: emptyList()
                val hasMatch = aliases.any { alias ->
                    ConfidenceScorer.computeConfidence(parsed.title, alias.title, null, tmdbYear) >= Constants.MATCH_CONFIDENCE_THRESHOLD
                }
                if (hasMatch) {
                    confidence = 1.0f
                }
            } catch (e: Exception) {
                // Ignore failure and fallback to initial confidence
            }
        }

        if (firstResult == null || confidence < Constants.MATCH_CONFIDENCE_THRESHOLD) {
            // Unidentified show -> all its episodes become unidentified
            for (eps in parsed.seasons.values) {
                for (ep in eps) {
                    handleUnidentified("${parsed.title} S?E${ep.episodeNumber}", ep.uri.toString(), MediaType.TV_SHOW, ep.subtitleUri?.toString())
                    emit(FileResult.Error("${parsed.title} S?E${ep.episodeNumber}", ScanStatus.MATCHED))
                }
            }
            return@flow
        }

        val showId = getOrCreateShow(firstResult.id, apiKey, confidence, parsed.title)

        for ((seasonNum, episodes) in parsed.seasons) {
            val (seasonId, seasonDetails) = getOrCreateSeason(showId, firstResult.id, seasonNum, apiKey)

            for (ep in episodes) {
                val fileUriString = ep.uri.toString()
                val existingEpisode = episodeDao.getByFilePath(fileUriString)
                if (existingEpisode != null) {
                    emit(FileResult.Skipped(parsed.title))
                    continue
                }

                val episodeInfo = seasonDetails?.episodes?.find { it.episodeNumber == ep.episodeNumber }

                val thumbnailPath = episodeInfo?.stillPath?.let { path ->
                    imageCacheManager.cacheEpisodeStill(
                        path,
                        "tv_${firstResult.id}_s${seasonNum}e${ep.episodeNumber}_still.jpg"
                    )
                }

                val entity = EpisodeEntity(
                    showId = showId,
                    seasonNumber = seasonNum,
                    episodeNumber = ep.episodeNumber,
                    title = episodeInfo?.name,
                    overview = episodeInfo?.overview,
                    runtime = episodeInfo?.runtime,
                    airDate = episodeInfo?.airDate,
                    thumbnailPath = thumbnailPath,
                    filePath = fileUriString,
                    externalSubtitlePath = ep.subtitleUri?.toString(),
                    preferredEngine = determineEngine(ep.uri)
                )
                emit(FileResult.Episode(entity))
            }
        }
    }

    private suspend fun getOrCreateShow(
        tmdbId: Int,
        apiKey: String,
        confidence: Float,
        parsedTitle: String
    ): Long {
        val lock = showLocks.computeIfAbsent(tmdbId) { Mutex() }
        return lock.withLock {
            val existingByTmdb = mediaDao.getByTmdbId(tmdbId)
            if (existingByTmdb != null) return@withLock existingByTmdb.id

            val details = withRetry { tmdbApiService.getTvDetails(tmdbId, apiKey) }
            val finalMediaType = details.inferMediaType()

            val posterPath = details.posterPath?.let { path ->
                imageCacheManager.cachePoster(path, "tv_${details.id}_poster.jpg")
            }
            val backdropPath = details.backdropPath?.let { path ->
                imageCacheManager.cacheBackdrop(path, "tv_${details.id}_backdrop.jpg")
            }

            val images = try {
                withRetry { tmdbApiService.getTvImages(tmdbId, apiKey) }
            } catch (e: Exception) {
                null
            }

            val logoInfo = images?.logos?.firstOrNull { it.iso6391 == "en" } ?: images?.logos?.firstOrNull()
            val logoLocalPath = logoInfo?.filePath?.let { path ->
                imageCacheManager.cacheLogo(path, "tv_${details.id}_logo.png")
            }

            val entity = details.toMediaEntity(
                mediaType = finalMediaType,
                confidence = confidence,
                posterLocalPath = posterPath,
                backdropLocalPath = backdropPath
            ).copy(logoPath = logoLocalPath)
            mediaDao.insert(entity)
        }
    }

    private suspend fun getOrCreateSeason(
        showId: Long,
        tmdbId: Int,
        seasonNumber: Int,
        apiKey: String
    ): Pair<Long, SeasonDetailsDto?> {
        val lock = seasonLocks.computeIfAbsent(Pair(showId, seasonNumber)) { Mutex() }
        return lock.withLock {
            val existing = seasonDao.getByShowAndSeason(showId, seasonNumber)

            val seasonDetails = try {
                withRetry { tmdbApiService.getSeasonDetails(tmdbId, seasonNumber, apiKey) }
            } catch (e: Exception) {
                null
            }

            if (existing != null) return@withLock Pair(existing.id, seasonDetails)

            val seasonEntity = SeasonEntity(
                showId = showId,
                seasonNumber = seasonNumber,
                name = seasonDetails?.name ?: "Season $seasonNumber",
                posterPath = seasonDetails?.posterPath?.let { path ->
                    imageCacheManager.cachePoster(path, "tv_${tmdbId}_s${seasonNumber}_poster.jpg")
                }
            )
            val seasonId = seasonDao.insert(seasonEntity)
            Pair(seasonId, seasonDetails)
        }
    }

    private suspend fun handleUnidentified(
        rawName: String,
        fileUri: String,
        mediaType: MediaType,
        subtitleUriString: String? = null
    ) {
        val existing = mediaDao.getByFilePath(fileUri)
        if (existing != null) return

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
                year = null,
                mediaType = mediaType.name,
                matchConfidence = 0f,
                isUnidentified = true,
                duration = null,
                certification = null,
                lastUpdated = System.currentTimeMillis(),
                filePath = fileUri,
                externalSubtitlePath = subtitleUriString,
                preferredEngine = determineEngine(Uri.parse(fileUri))
            )
        )
    }

    /**
     * Probes the media file's codecs and returns the appropriate engine name.
     * If all video/audio tracks are hardware-decodable → EXOPLAYER.
     * Otherwise → LIBVLC (which has its own software decoders).
     */
    private suspend fun determineEngine(uri: Uri): String {
        return if (CodecCapabilityChecker.canHardwareDecode(context, uri)) {
            EngineType.EXOPLAYER.name
        } else {
            EngineType.LIBVLC.name
        }
    }
}
