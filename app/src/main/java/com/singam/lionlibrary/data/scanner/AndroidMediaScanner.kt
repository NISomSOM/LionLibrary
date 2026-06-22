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
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

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

        for ((index, file) in allFiles.withIndex()) {
            val fileUriString = file.uri.toString()

            emit(
                ScanProgress(
                    total = total,
                    processed = index,
                    currentFile = file.displayName,
                    status = ScanStatus.SCANNING
                )
            )

            try {
                // Check if already in DB
                val existingMedia = mediaDao.getByFilePath(fileUriString)
                if (existingMedia != null) {
                    emit(ScanProgress(total, index + 1, file.displayName, ScanStatus.SKIPPED))
                    continue
                }

                // Also check episodes table for TV/Anime
                if (file.mediaType != MediaType.MOVIE) {
                    val existingEpisode = episodeDao.getByFilePath(fileUriString)
                    if (existingEpisode != null) {
                        emit(ScanProgress(total, index + 1, file.displayName, ScanStatus.SKIPPED))
                        continue
                    }
                }

                // Parse filename
                val parsed = fileNameParser.parse(file)

                when (parsed) {
                    is ParsedFile.Movie -> handleMovie(parsed, fileUriString, apiKey, file.mediaType)
                    is ParsedFile.Episode -> handleEpisode(parsed, fileUriString, apiKey, file.mediaType)
                    is ParsedFile.Unknown -> handleUnidentified(parsed.rawName, fileUriString, file.mediaType)
                }

                emit(ScanProgress(total, index + 1, file.displayName, ScanStatus.MATCHED))
            } catch (e: retrofit2.HttpException) {
                if (e.code() == 401) {
                    emit(ScanProgress(total, index + 1, file.displayName, ScanStatus.INVALID_API_KEY))
                    return@flow
                } else {
                    handleUnidentified(file.displayName, fileUriString, file.mediaType)
                    emit(ScanProgress(total, index + 1, file.displayName, ScanStatus.ERROR))
                }
            } catch (e: java.io.IOException) {
                handleUnidentified(file.displayName, fileUriString, file.mediaType)
                // Save all remaining unprocessed files as unidentified
                for (j in (index + 1) until allFiles.size) {
                    val remaining = allFiles[j]
                    handleUnidentified(remaining.displayName, remaining.uri.toString(), remaining.mediaType)
                }
                emit(ScanProgress(total, total, "", ScanStatus.NO_INTERNET))
                return@flow
            } catch (e: Exception) {
                // Save as unidentified on error
                handleUnidentified(file.displayName, fileUriString, file.mediaType)
                emit(ScanProgress(total, index + 1, file.displayName, ScanStatus.ERROR))
            }
        }

        // Update last scan time
        settingsRepository.setLastScanTime(System.currentTimeMillis())
        emit(ScanProgress(total, total, "", ScanStatus.COMPLETE))
    }.flowOn(Dispatchers.IO)


    private suspend fun handleMovie(
        parsed: ParsedFile.Movie,
        fileUri: String,
        apiKey: String,
        mediaType: MediaType
    ) {
        try {
            val searchResult = tmdbApiService.searchMovie(apiKey, parsed.title, parsed.year)
            val firstResult = searchResult.results.firstOrNull()

            if (firstResult == null) {
                handleUnidentified(parsed.title, fileUri, mediaType)
                return
            }

            val tmdbYear = firstResult.releaseDate?.take(4)?.toIntOrNull()
            val confidence = ConfidenceScorer.computeConfidence(
                parsed.title, firstResult.title, parsed.year, tmdbYear
            )

            if (confidence < Constants.MATCH_CONFIDENCE_THRESHOLD) {
                handleUnidentified(parsed.title, fileUri, mediaType)
                return
            }

            // Check for existing entry by TMDB ID (dedup)
            val existing = mediaDao.getByTmdbId(firstResult.id)
            if (existing != null) return

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

            val entity = details.toMediaEntity(
                mediaType = mediaType,
                confidence = confidence,
                posterLocalPath = posterPath,
                backdropLocalPath = backdropPath,
                filePath = fileUri
            ).copy(logoPath = logoLocalPath)
            mediaDao.insert(entity)
        } catch (e: retrofit2.HttpException) {
            throw e
        } catch (e: java.io.IOException) {
            throw e
        } catch (e: Exception) {
            handleUnidentified(parsed.title, fileUri, mediaType)
        }
    }


    private suspend fun handleEpisode(
        parsed: ParsedFile.Episode,
        fileUri: String,
        apiKey: String,
        mediaType: MediaType
    ) {
        try {
            val searchResult = tmdbApiService.searchTv(apiKey, parsed.title)
            val firstResult = searchResult.results.firstOrNull()

            if (firstResult == null) {
                handleUnidentified(parsed.title, fileUri, mediaType)
                return
            }

            val tmdbYear = firstResult.firstAirDate?.take(4)?.toIntOrNull()
            val confidence = ConfidenceScorer.computeConfidence(
                parsed.title, firstResult.name, null, tmdbYear
            )

            if (confidence < Constants.MATCH_CONFIDENCE_THRESHOLD) {
                handleUnidentified(parsed.title, fileUri, mediaType)
                return
            }

            // Get or create show entry
            val showId = getOrCreateShow(firstResult.id, apiKey, mediaType, confidence, parsed.title)

            // Get or create season entry — also returns the season details DTO to avoid a duplicate API call
            val (_, seasonDetails) = getOrCreateSeason(showId, firstResult.id, parsed.season, apiKey)

            val episodeInfo = seasonDetails?.episodes?.find { it.episodeNumber == parsed.episode }

            val thumbnailPath = episodeInfo?.stillPath?.let { path ->
                imageCacheManager.cacheEpisodeStill(path, "tv_${firstResult.id}_s${parsed.season}e${parsed.episode}_still.jpg")
            }

            episodeDao.insert(
                EpisodeEntity(
                    showId = showId,
                    seasonNumber = parsed.season,
                    episodeNumber = parsed.episode,
                    title = episodeInfo?.name,
                    overview = episodeInfo?.overview,
                    runtime = episodeInfo?.runtime,
                    thumbnailPath = thumbnailPath,
                    filePath = fileUri
                )
            )
        } catch (e: retrofit2.HttpException) {
            throw e
        } catch (e: java.io.IOException) {
            throw e
        } catch (e: Exception) {
            handleUnidentified(parsed.title, fileUri, mediaType)
        }
    }


    // Finds or creates a MediaEntity for a TV show / anime. Returns the local DB ID.
    private suspend fun getOrCreateShow(
        tmdbId: Int,
        apiKey: String,
        mediaType: MediaType,
        confidence: Float,
        parsedTitle: String
    ): Long {
        // Check if already exists by TMDB ID
        val existingByTmdb = mediaDao.getByTmdbId(tmdbId)
        if (existingByTmdb != null) return existingByTmdb.id

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
        return mediaDao.insert(entity)
    }

    // Gets or creates a SeasonEntity. Returns the ID and the DTO so we don't hit the API twice.
    private suspend fun getOrCreateSeason(
        showId: Long,
        tmdbId: Int,
        seasonNumber: Int,
        apiKey: String
    ): Pair<Long, SeasonDetailsDto?> {
        // Check if the season already exists in the database
        val existing = seasonDao.getByShowAndSeason(showId, seasonNumber)

        val seasonDetails = try {
            tmdbApiService.getSeasonDetails(tmdbId, seasonNumber, apiKey)
        } catch (e: Exception) {
            null
        }

        if (existing != null) return Pair(existing.id, seasonDetails)

        val seasonEntity = SeasonEntity(
            showId = showId,
            seasonNumber = seasonNumber,
            name = seasonDetails?.name ?: "Season $seasonNumber",
            posterPath = seasonDetails?.posterPath?.let { path ->
                imageCacheManager.cachePoster(path, "tv_${tmdbId}_s${seasonNumber}_poster.jpg")
            }
        )
        val seasonId = seasonDao.insert(seasonEntity)
        return Pair(seasonId, seasonDetails)
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
                lastUpdated = System.currentTimeMillis(),
                filePath = fileUri
            )
        )
    }
}

