package com.singam.lionlibrary.data.scanner

import android.net.Uri
import com.singam.lionlibrary.data.local.db.dao.EpisodeDao
import com.singam.lionlibrary.data.local.db.dao.MediaDao
import com.singam.lionlibrary.data.local.db.dao.SeasonDao
import com.singam.lionlibrary.data.local.db.entity.EpisodeEntity
import com.singam.lionlibrary.data.local.db.entity.MediaEntity
import com.singam.lionlibrary.data.local.db.entity.SeasonEntity
import com.singam.lionlibrary.data.mapper.toMediaEntity
import com.singam.lionlibrary.data.remote.api.TmdbApiService
import com.singam.lionlibrary.data.remote.dto.SeasonDetailsDto
import com.singam.lionlibrary.domain.model.MediaType
import com.singam.lionlibrary.domain.model.ScanProgress
import com.singam.lionlibrary.domain.model.ScanStatus
import com.singam.lionlibrary.domain.repository.SettingsRepository
import com.singam.lionlibrary.domain.usecase.ScanLibraryUseCase
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
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

// Main scanner logic that ties everything together (SAF, TMDB, DB, etc).
// Implements ScanLibraryUseCase for injection.
class AndroidMediaScanner(
    private val folderScanner: FolderScanner,
    private val fileNameParser: FileNameParser,
    private val tmdbApiService: TmdbApiService,
    private val imageCacheManager: ImageCacheManager,
    private val mediaDao: MediaDao,
    private val seasonDao: SeasonDao,
    private val episodeDao: EpisodeDao,
    private val settingsRepository: SettingsRepository
) : ScanLibraryUseCase {

    // Per-tmdbId locks — prevent concurrent coroutines for the same show from racing
    // into getOrCreateShow / getOrCreateSeason and inserting duplicate rows.
    // ConcurrentHashMap is safe for concurrent reads/writes; getOrPut is atomic.
    private val showLocks = ConcurrentHashMap<Int, Mutex>()
    private val seasonLocks = ConcurrentHashMap<Pair<Long, Int>, Mutex>()

    // Carries the result of processing a single file back to the serial collector.
    private sealed interface FileResult {
        data class Media(val entity: MediaEntity) : FileResult
        data class Episode(val entity: EpisodeEntity) : FileResult
        data class Skipped(val displayName: String) : FileResult
        data class Error(val displayName: String, val status: ScanStatus) : FileResult
        // Signals the entire scan should abort with a terminal status
        data class FatalAbort(val status: ScanStatus, val displayName: String) : FileResult
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override operator fun invoke(): Flow<ScanProgress> = flow {
        val apiKey = settingsRepository.tmdbApiKey.first()
        if (apiKey.isBlank()) {
            emit(ScanProgress(0, 0, "", ScanStatus.API_KEY_MISSING))
            return@flow
        }

        // Collect folder URIs
        val moviesFolderUri = settingsRepository.moviesFolderUri.first()
        val showsFolderUri = settingsRepository.showsFolderUri.first()
        val animeFolderUri = settingsRepository.animeFolderUri.first()

        // Scan all configured folders
        val allFiles = mutableListOf<ScannedFile>()

        try {
            if (moviesFolderUri.isNotBlank()) {
                allFiles += folderScanner.scanFolder(Uri.parse(moviesFolderUri), MediaType.MOVIE)
            }
            if (showsFolderUri.isNotBlank()) {
                allFiles += folderScanner.scanFolder(Uri.parse(showsFolderUri), MediaType.TV_SHOW)
            }
            if (animeFolderUri.isNotBlank()) {
                allFiles += folderScanner.scanFolder(Uri.parse(animeFolderUri), MediaType.ANIME)
            }
        } catch (e: FolderPermissionException) {
            emit(ScanProgress(0, 0, "", ScanStatus.PERMISSION_REVOKED))
            return@flow
        }

        val total = allFiles.size
        if (total == 0) {
            emit(ScanProgress(0, 0, "", ScanStatus.COMPLETE))
            return@flow
        }

        emit(ScanProgress(total, 0, "", ScanStatus.SCANNING))

        // Thread-safe counter — incremented from multiple coroutines inside flatMapMerge
        val processedCount = AtomicInteger(0)

        // Batch buffers — only written from the serial .collect {} lambda, so no locking needed
        val mediaBatch = mutableListOf<MediaEntity>()
        val episodeBatch = mutableListOf<EpisodeEntity>()

        // Flush helpers
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

        allFiles
            .asFlow()
            .flatMapMerge(concurrency = Constants.SCAN_CONCURRENCY) { file ->
                flow {
                    val fileUriString = file.uri.toString()

                    try {
                        // Skip files already in DB — no TMDB call made
                        val existingMedia = mediaDao.getByFilePath(fileUriString)
                        if (existingMedia != null) {
                            emit(FileResult.Skipped(file.displayName))
                            return@flow
                        }

                        // Also check episodes table for TV/Anime
                        if (file.mediaType != MediaType.MOVIE) {
                            val existingEpisode = episodeDao.getByFilePath(fileUriString)
                            if (existingEpisode != null) {
                                emit(FileResult.Skipped(file.displayName))
                                return@flow
                            }
                        }

                        // Parse filename
                        val parsed = fileNameParser.parse(file)

                        val result: FileResult = when (parsed) {
                            is ParsedFile.Movie -> {
                                val entity = processMovie(parsed, fileUriString, apiKey, file.mediaType)
                                if (entity != null) FileResult.Media(entity)
                                else FileResult.Error(file.displayName, ScanStatus.MATCHED)
                            }
                            is ParsedFile.Episode -> {
                                val entity = processEpisode(parsed, fileUriString, apiKey, file.mediaType)
                                if (entity != null) FileResult.Episode(entity)
                                else FileResult.Error(file.displayName, ScanStatus.MATCHED)
                            }
                            is ParsedFile.Unknown -> {
                                handleUnidentified(parsed.rawName, fileUriString, file.mediaType)
                                FileResult.Error(file.displayName, ScanStatus.MATCHED)
                            }
                        }
                        emit(result)
                    } catch (e: retrofit2.HttpException) {
                        if (e.code() == 401) {
                            emit(FileResult.FatalAbort(ScanStatus.INVALID_API_KEY, file.displayName))
                        } else {
                            handleUnidentified(file.displayName, fileUriString, file.mediaType)
                            emit(FileResult.Error(file.displayName, ScanStatus.ERROR))
                        }
                    } catch (e: java.io.IOException) {
                        handleUnidentified(file.displayName, fileUriString, file.mediaType)
                        emit(FileResult.FatalAbort(ScanStatus.NO_INTERNET, file.displayName))
                    } catch (e: Exception) {
                        // Save as unidentified on error, continue scanning
                        handleUnidentified(file.displayName, fileUriString, file.mediaType)
                        emit(FileResult.Error(file.displayName, ScanStatus.ERROR))
                    }
                }
            }
            .collect { result ->
                // .collect{} is serial — safe to mutate batch lists without locking

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
                        // Flush whatever was buffered before stopping
                        flushMedia()
                        flushEpisodes()
                        abortStatus = result.status
                        emit(ScanProgress(total, processedCount.get(), result.displayName, result.status))
                    }
                }
            }

        // Flush remaining batches that didn't fill a full buffer of 20
        flushMedia()
        flushEpisodes()

        if (abortStatus != null) return@flow

        // Update last scan time
        settingsRepository.setLastScanTime(System.currentTimeMillis())
        emit(ScanProgress(total, total, "", ScanStatus.COMPLETE))
    }.flowOn(Dispatchers.IO)


    // Processes a movie file — returns the MediaEntity to be batched, or null if unidentified.
    private suspend fun processMovie(
        parsed: ParsedFile.Movie,
        fileUri: String,
        apiKey: String,
        mediaType: MediaType
    ): MediaEntity? {
        return try {
            val searchResult = tmdbApiService.searchMovie(apiKey, parsed.title, parsed.year)
            val firstResult = searchResult.results.firstOrNull()

            if (firstResult == null) {
                handleUnidentified(parsed.title, fileUri, mediaType)
                return null
            }

            val tmdbYear = firstResult.releaseDate?.take(4)?.toIntOrNull()
            val confidence = ConfidenceScorer.computeConfidence(
                parsed.title, firstResult.title, parsed.year, tmdbYear
            )

            if (confidence < Constants.MATCH_CONFIDENCE_THRESHOLD) {
                handleUnidentified(parsed.title, fileUri, mediaType)
                return null
            }

            // Check for existing entry by TMDB ID (dedup)
            val existing = mediaDao.getByTmdbId(firstResult.id)
            if (existing != null) return null

            // Fetch full details for genres
            val details = tmdbApiService.getMovieDetails(firstResult.id, apiKey)

            // Cache images
            val posterPath = details.posterPath?.let { path ->
                imageCacheManager.cachePoster(path, "movie_${details.id}_poster.jpg")
            }
            val backdropPath = details.backdropPath?.let { path ->
                imageCacheManager.cacheBackdrop(path, "movie_${details.id}_backdrop.jpg")
            }

            // Fetch and cache logo
            val images = try {
                tmdbApiService.getMovieImages(details.id, apiKey)
            } catch (e: Exception) {
                null
            }

            val logoInfo = images?.logos?.firstOrNull { it.iso6391 == "en" } ?: images?.logos?.firstOrNull()
            val logoLocalPath = logoInfo?.filePath?.let { path ->
                imageCacheManager.cacheLogo(path, "movie_${details.id}_logo.png")
            }

            details.toMediaEntity(
                mediaType = mediaType,
                confidence = confidence,
                posterLocalPath = posterPath,
                backdropLocalPath = backdropPath,
                filePath = fileUri
            ).copy(logoPath = logoLocalPath)
        } catch (e: retrofit2.HttpException) {
            throw e
        } catch (e: java.io.IOException) {
            throw e
        } catch (e: Exception) {
            handleUnidentified(parsed.title, fileUri, mediaType)
            null
        }
    }


    // Processes an episode file — returns the EpisodeEntity to be batched, or null if unidentified.
    private suspend fun processEpisode(
        parsed: ParsedFile.Episode,
        fileUri: String,
        apiKey: String,
        mediaType: MediaType
    ): EpisodeEntity? {
        return try {
            val searchResult = tmdbApiService.searchTv(apiKey, parsed.title)
            val firstResult = searchResult.results.firstOrNull()

            if (firstResult == null) {
                handleUnidentified(parsed.title, fileUri, mediaType)
                return null
            }

            val tmdbYear = firstResult.firstAirDate?.take(4)?.toIntOrNull()
            val confidence = ConfidenceScorer.computeConfidence(
                parsed.title, firstResult.name, null, tmdbYear
            )

            if (confidence < Constants.MATCH_CONFIDENCE_THRESHOLD) {
                handleUnidentified(parsed.title, fileUri, mediaType)
                return null
            }

            // Get or create show entry — individual insert needed (returns ID for linking)
            val showId = getOrCreateShow(firstResult.id, apiKey, mediaType, confidence, parsed.title)

            // Get or create season entry — individual insert needed (returns ID + DTO)
            val (_, seasonDetails) = getOrCreateSeason(showId, firstResult.id, parsed.season, apiKey)

            val episodeInfo = seasonDetails?.episodes?.find { it.episodeNumber == parsed.episode }

            val thumbnailPath = episodeInfo?.stillPath?.let { path ->
                imageCacheManager.cacheEpisodeStill(
                    path,
                    "tv_${firstResult.id}_s${parsed.season}e${parsed.episode}_still.jpg"
                )
            }

            EpisodeEntity(
                showId = showId,
                seasonNumber = parsed.season,
                episodeNumber = parsed.episode,
                title = episodeInfo?.name,
                overview = episodeInfo?.overview,
                runtime = episodeInfo?.runtime,
                airDate = episodeInfo?.airDate,
                thumbnailPath = thumbnailPath,
                filePath = fileUri
            )
        } catch (e: retrofit2.HttpException) {
            throw e
        } catch (e: java.io.IOException) {
            throw e
        } catch (e: Exception) {
            handleUnidentified(parsed.title, fileUri, mediaType)
            null
        }
    }


    // Finds or creates a MediaEntity for a TV show / anime. Returns the local DB ID.
    //
    // THREAD SAFETY: guarded by a per-tmdbId Mutex so that concurrent coroutines processing
    // episodes of the same show don't all see getByTmdbId()==null and each insert a duplicate row.
    // TV shows have filePath=null, so SQLite's UNIQUE constraint on filePath does NOT prevent
    // duplicates (NULL != NULL in SQL). The mutex is the only guard.
    private suspend fun getOrCreateShow(
        tmdbId: Int,
        apiKey: String,
        mediaType: MediaType,
        confidence: Float,
        parsedTitle: String
    ): Long {
        val lock = showLocks.computeIfAbsent(tmdbId) { Mutex() }
        return lock.withLock {
            // Re-check inside the lock: another coroutine may have inserted it
            // while we were waiting to acquire the lock.
            val existingByTmdb = mediaDao.getByTmdbId(tmdbId)
            if (existingByTmdb != null) return@withLock existingByTmdb.id

            val details = tmdbApiService.getTvDetails(tmdbId, apiKey)

            val posterPath = details.posterPath?.let { path ->
                imageCacheManager.cachePoster(path, "tv_${details.id}_poster.jpg")
            }
            val backdropPath = details.backdropPath?.let { path ->
                imageCacheManager.cacheBackdrop(path, "tv_${details.id}_backdrop.jpg")
            }

            // Fetch and cache logo
            val images = try {
                tmdbApiService.getTvImages(tmdbId, apiKey)
            } catch (e: Exception) {
                null
            }

            val logoInfo = images?.logos?.firstOrNull { it.iso6391 == "en" } ?: images?.logos?.firstOrNull()
            val logoLocalPath = logoInfo?.filePath?.let { path ->
                imageCacheManager.cacheLogo(path, "tv_${details.id}_logo.png")
            }

            val entity = details.toMediaEntity(
                mediaType = mediaType,
                confidence = confidence,
                posterLocalPath = posterPath,
                backdropLocalPath = backdropPath
            ).copy(title = parsedTitle, logoPath = logoLocalPath)
            mediaDao.insert(entity)
        }
    }

    // Gets or creates a SeasonEntity. Returns the ID and the DTO so we don't hit the API twice.
    //
    // THREAD SAFETY: guarded by a per-(showId, seasonNumber) Mutex for the same reason as
    // getOrCreateShow — concurrent episodes in the same season must not race on insert.
    private suspend fun getOrCreateSeason(
        showId: Long,
        tmdbId: Int,
        seasonNumber: Int,
        apiKey: String
    ): Pair<Long, SeasonDetailsDto?> {
        val lock = seasonLocks.computeIfAbsent(Pair(showId, seasonNumber)) { Mutex() }
        return lock.withLock {
            // Re-check inside the lock.
            val existing = seasonDao.getByShowAndSeason(showId, seasonNumber)

            val seasonDetails = try {
                tmdbApiService.getSeasonDetails(tmdbId, seasonNumber, apiKey)
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

    // Fallback for files we couldn't identify.
    private suspend fun handleUnidentified(
        rawName: String,
        fileUri: String,
        mediaType: MediaType
    ) {
        // Check if already saved as unidentified
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
                filePath = fileUri
            )
        )
    }
}
