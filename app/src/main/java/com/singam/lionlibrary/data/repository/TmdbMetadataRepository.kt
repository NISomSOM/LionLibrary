package com.singam.lionlibrary.data.repository

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.singam.lionlibrary.data.local.db.MediaDatabase
import com.singam.lionlibrary.data.local.db.entity.EpisodeEntity
import com.singam.lionlibrary.data.local.db.entity.SeasonEntity
import com.singam.lionlibrary.data.mapper.*
import com.singam.lionlibrary.data.remote.api.TmdbApiService
import com.singam.lionlibrary.data.scanner.ImageCacheManager
import com.singam.lionlibrary.data.scanner.TmdbMatcher
import com.singam.lionlibrary.domain.model.*
import com.singam.lionlibrary.domain.repository.*
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import retrofit2.HttpException

class TmdbMetadataRepository(
    private val context: Context,
    private val api: TmdbApiService,
    private val database: MediaDatabase,
    private val settings: SettingsRepository,
    private val images: ImageCacheManager,
) : MetadataRepository {
    private val matcher = TmdbMatcher(api)

    override suspend fun validateKey(key: String): OperationResult<Unit, DataError> = safely {
        if (key.isBlank()) return@safely OperationResult.Failure(DataError.UNAUTHORIZED)
        api.validateKey(key)
        OperationResult.Success(Unit)
    }

    override suspend fun search(
        title: String,
        type: MediaType,
    ): OperationResult<List<MetadataCandidate>, DataError> = safely {
        val key = settings.tmdbApiKey.first()
        if (key.isBlank()) return@safely OperationResult.Failure(DataError.UNAUTHORIZED)
        val results =
            if (type == MediaType.MOVIE)
                api.searchMovie(key, title).results.map {
                    MetadataCandidate(
                        it.id,
                        it.title,
                        it.releaseDate?.take(4)?.toIntOrNull(),
                        MediaType.MOVIE,
                    )
                }
            else
                api.searchTv(key, title).results.map {
                    MetadataCandidate(
                        it.id,
                        it.name,
                        it.firstAirDate?.take(4)?.toIntOrNull(),
                        MediaType.TV_SHOW,
                    )
                }
        OperationResult.Success(results)
    }

    override suspend fun retry(mediaId: Long): OperationResult<Unit, DataError> = safely {
        val media =
            database.mediaDao().getById(mediaId)
                ?: return@safely OperationResult.Failure(DataError.NOT_FOUND)
        val key = settings.tmdbApiKey.first()
        if (key.isBlank()) return@safely OperationResult.Failure(DataError.UNAUTHORIZED)
        val candidate =
            if (media.mediaType == "MOVIE") {
                matcher.movie(media.title, media.year, key)?.candidate?.let {
                    MetadataCandidate(it.id, it.title, media.year, MediaType.MOVIE)
                }
            } else
                matcher.show(media.sourceShowTitle ?: media.title, key)?.candidate?.let {
                    MetadataCandidate(it.id, it.name, null, MediaType.TV_SHOW)
                }
        if (candidate == null) OperationResult.Failure(DataError.NOT_FOUND)
        else identify(mediaId, candidate, null)
    }

    override suspend fun identify(
        mediaId: Long,
        candidate: MetadataCandidate,
        assignment: EpisodeAssignment?,
    ): OperationResult<Unit, DataError> = safely {
        val dao = database.mediaDao()
        val current =
            dao.getById(mediaId) ?: return@safely OperationResult.Failure(DataError.NOT_FOUND)
        val key = settings.tmdbApiKey.first()
        if (key.isBlank()) return@safely OperationResult.Failure(DataError.UNAUTHORIZED)
        if (candidate.type == MediaType.MOVIE) {
            val details = api.getMovieDetails(candidate.tmdbId, key)
            val poster =
                details.posterPath?.let { images.cachePoster(it, "movie_${details.id}_poster.jpg") }
            val backdrop =
                details.backdropPath?.let {
                    images.cacheBackdrop(it, "movie_${details.id}_backdrop.jpg")
                }
            val logo =
                try {
                    api.getMovieImages(details.id, key)
                        .logos
                        ?.let { logos ->
                            logos.firstOrNull { it.iso6391 == "en" } ?: logos.firstOrNull()
                        }
                        ?.filePath
                        ?.let { images.cacheLogo(it, "movie_${details.id}_logo.png") }
                } catch (error: HttpException) {
                    if (error.code() == 404) null else throw error
                }
            val updated =
                details
                    .toMediaEntity(MediaType.MOVIE, 1f, poster, backdrop, current.filePath)
                    .copy(
                        id = current.id,
                        externalSubtitlePath = current.externalSubtitlePath,
                        preferredEngine = current.preferredEngine,
                        isWatchlisted = current.isWatchlisted,
                        addedAt = current.addedAt,
                        logoPath = logo ?: current.logoPath.takeIf { current.tmdbId == details.id },
                        letterboxdRating =
                            current.letterboxdRating.takeIf { current.tmdbId == details.id },
                        letterboxdUpdatedAt =
                            current.letterboxdUpdatedAt.takeIf { current.tmdbId == details.id },
                    )
            dao.update(updated)
        } else {
            val details = api.getTvDetails(candidate.tmdbId, key)
            val logo =
                try {
                    api.getTvImages(details.id, key)
                        .logos
                        ?.let { logos ->
                            logos.firstOrNull { it.iso6391 == "en" } ?: logos.firstOrNull()
                        }
                        ?.filePath
                        ?.let { images.cacheLogo(it, "tv_${details.id}_logo.png") }
                } catch (error: HttpException) {
                    if (error.code() == 404) null else throw error
                }
            val poster =
                details.posterPath?.let { images.cachePoster(it, "tv_${details.id}_poster.jpg") }
            val backdrop =
                details.backdropPath?.let {
                    images.cacheBackdrop(it, "tv_${details.id}_backdrop.jpg")
                }
            val existing = if (current.isUnidentified) dao.getTvByTmdbId(details.id) else null
            val rawGroup =
                current.sourceShowTitle?.let { dao.getUnidentifiedShow(it) } ?: listOf(current)
            val group =
                rawGroup
                    .map { pending ->
                        if (pending.id == current.id && assignment != null)
                            pending.copy(
                                sourceSeason = assignment.season,
                                sourceEpisode = assignment.episode,
                            )
                        else pending
                    }
                    .filter { !current.isUnidentified || it.sourceEpisode != null }
            if (current.isUnidentified && group.none { it.id == current.id })
                return@safely OperationResult.Failure(DataError.NOT_FOUND)
            val mapped =
                details
                    .toMediaEntity(details.inferMediaType(), 1f, poster, backdrop)
                    .copy(
                        id = existing?.id ?: if (current.isUnidentified) 0L else current.id,
                        isWatchlisted =
                            current.isWatchlisted ||
                                existing?.isWatchlisted == true ||
                                group.any { it.isWatchlisted },
                        addedAt = existing?.addedAt ?: current.addedAt,
                        logoPath = logo ?: current.logoPath.takeIf { current.tmdbId == details.id },
                    )
            val seasonNumbers =
                if (current.isUnidentified) group.mapNotNull { it.sourceSeason }.distinct()
                else
                    database
                        .episodeDao()
                        .getAllForShow(current.id)
                        .first()
                        .map { it.seasonNumber }
                        .distinct()
            val seasons = seasonNumbers.associateWith {
                try {
                    api.getSeasonDetails(details.id, it, key)
                } catch (error: HttpException) {
                    if (error.code() == 404) null else throw error
                }
            }
            val seasonPosters = seasons.mapValues { (number, season) ->
                season?.posterPath?.let {
                    images.cachePoster(it, "tv_${details.id}_s${number}_poster.jpg")
                }
            }
            val localEpisodes =
                if (current.isUnidentified)
                    group.map { (it.sourceSeason ?: 1) to it.sourceEpisode }.toSet()
                else
                    database
                        .episodeDao()
                        .getAllForShow(current.id)
                        .first()
                        .map { it.seasonNumber to it.episodeNumber }
                        .toSet()
            val stills = mutableMapOf<Pair<Int, Int>, String?>()
            seasons.forEach { (number, season) ->
                season
                    ?.episodes
                    .orEmpty()
                    .filter { (number to it.episodeNumber) in localEpisodes }
                    .forEach { ep ->
                        stills[number to ep.episodeNumber] =
                            ep.stillPath?.let {
                                images.cacheEpisodeStill(
                                    it,
                                    "tv_${details.id}_s${number}e${ep.episodeNumber}_still.jpg",
                                )
                            }
                    }
            }
            database.withTransaction {
                val showId =
                    if (mapped.id != 0L) {
                        dao.update(mapped)
                        mapped.id
                    } else dao.insert(mapped)
                seasons.forEach { (number, season) ->
                    val previous = database.seasonDao().getByShowAndSeason(showId, number)
                    if (previous != null)
                        database
                            .seasonDao()
                            .update(
                                previous.copy(
                                    name = season?.name ?: previous.name,
                                    posterPath = seasonPosters[number] ?: previous.posterPath,
                                )
                            )
                    else
                        database
                            .seasonDao()
                            .insert(
                                SeasonEntity(
                                    showId = showId,
                                    seasonNumber = number,
                                    name = season?.name,
                                    posterPath = seasonPosters[number],
                                )
                            )
                }
                if (current.isUnidentified)
                    group.forEach { pending ->
                        val number = pending.sourceEpisode ?: return@forEach
                        val season = pending.sourceSeason ?: 1
                        val info = seasons[season]?.episodes?.find { it.episodeNumber == number }
                        val path = pending.filePath ?: return@forEach
                        val known = database.episodeDao().getByFilePath(path)
                        val insertedId =
                            database
                                .episodeDao()
                                .insert(
                                    EpisodeEntity(
                                        id = known?.id ?: 0L,
                                        showId = showId,
                                        seasonNumber = season,
                                        episodeNumber = number,
                                        episodeEndNumber = pending.sourceEpisodeEnd,
                                        title = info?.name ?: pending.title,
                                        overview = info?.overview,
                                        runtime = info?.runtime,
                                        airDate = info?.airDate,
                                        thumbnailPath = stills[season to number],
                                        filePath = path,
                                        externalSubtitlePath = pending.externalSubtitlePath,
                                        preferredEngine = pending.preferredEngine,
                                    )
                                )
                        val epId = known?.id ?: insertedId
                        database.watchProgressDao().getProgress(pending.id, 0L)?.let { progress ->
                            database
                                .watchProgressDao()
                                .upsert(progress.copy(mediaId = showId, episodeId = epId))
                        }
                        database.watchProgressDao().deleteProgressForMedia(pending.id)
                        dao.deleteById(pending.id)
                    }
                else
                    database.episodeDao().getAllForShow(showId).first().forEach { ep ->
                        val info =
                            seasons[ep.seasonNumber]?.episodes?.find {
                                it.episodeNumber == ep.episodeNumber
                            }
                        if (info != null)
                            database
                                .episodeDao()
                                .insert(
                                    ep.copy(
                                        title = info.name,
                                        overview = info.overview,
                                        runtime = info.runtime,
                                        airDate = info.airDate,
                                        thumbnailPath = stills[ep.seasonNumber to ep.episodeNumber],
                                    )
                                )
                    }
            }
        }
        OperationResult.Success(Unit)
    }

    override suspend fun refreshArtwork(): OperationResult<Int, DataError> = safely {
        val key = settings.tmdbApiKey.first()
        if (key.isBlank()) return@safely OperationResult.Failure(DataError.UNAUTHORIZED)
        val items =
            database.mediaDao().getAll().first().filter { !it.isUnidentified && it.tmdbId != null }
        var refreshed = 0
        var failure: DataError? = null
        for (item in items) {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            val result = safely {
                val id = item.tmdbId!!
                val movie = item.mediaType == "MOVIE"
                val episodes =
                    if (movie) emptyList() else database.episodeDao().getAllForShow(item.id).first()
                val seasons =
                    if (movie) emptyList()
                    else database.seasonDao().getSeasonsForShow(item.id).first()
                val missingPoster = !images.isUsable(item.posterPath)
                val missingBackdrop = !images.isUsable(item.backdropPath)
                val missingLogo = !images.isUsable(item.logoPath)
                val missingSeasons = seasons.filter { !images.isUsable(it.posterPath) }
                val missingEpisodes = episodes.filter { !images.isUsable(it.thumbnailPath) }
                if (
                    !missingPoster &&
                        !missingBackdrop &&
                        !missingLogo &&
                        missingSeasons.isEmpty() &&
                        missingEpisodes.isEmpty()
                )
                    return@safely OperationResult.Success(false)
                val prefix = if (movie) "movie_$id" else "tv_$id"
                var changed = false
                var poster: String? = null
                var backdrop: String? = null
                if (missingPoster || missingBackdrop) {
                    val paths =
                        if (movie)
                            api.getMovieDetails(id, key).let { it.posterPath to it.backdropPath }
                        else api.getTvDetails(id, key).let { it.posterPath to it.backdropPath }
                    if (missingPoster)
                        poster = paths.first?.let { images.cachePoster(it, "${prefix}_poster.jpg") }
                    if (missingBackdrop)
                        backdrop =
                            paths.second?.let { images.cacheBackdrop(it, "${prefix}_backdrop.jpg") }
                    database.mediaDao().updateArtwork(item.id, poster, backdrop, null)
                    changed = poster != null || backdrop != null
                }
                if (missingLogo) {
                    val info = optionalArtwork {
                        if (movie) api.getMovieImages(id, key) else api.getTvImages(id, key)
                    }
                    val logos = info?.logos.orEmpty()
                    val remote =
                        (logos.firstOrNull { it.iso6391 == "en" }
                                ?: logos.firstOrNull { it.iso6391 == null }
                                ?: logos.firstOrNull())
                            ?.filePath
                    val logo = remote?.let { images.cacheLogo(it, "${prefix}_logo.png") }
                    if (logo != null) {
                        database.mediaDao().updateArtwork(item.id, null, null, logo)
                        changed = true
                    }
                }
                (missingSeasons.map { it.seasonNumber } + missingEpisodes.map { it.seasonNumber })
                    .distinct()
                    .forEach { number ->
                        val remote = optionalArtwork { api.getSeasonDetails(id, number, key) }
                        missingSeasons
                            .filter { it.seasonNumber == number }
                            .forEach { season ->
                                remote
                                    ?.posterPath
                                    ?.let {
                                        images.cachePoster(it, "${prefix}_s${number}_poster.jpg")
                                    }
                                    ?.let {
                                        database.seasonDao().updateArtwork(season.id, it)
                                        changed = true
                                    }
                            }
                        missingEpisodes
                            .filter { it.seasonNumber == number }
                            .forEach { episode ->
                                remote
                                    ?.episodes
                                    ?.find { it.episodeNumber == episode.episodeNumber }
                                    ?.stillPath
                                    ?.let {
                                        images.cacheEpisodeStill(
                                            it,
                                            "${prefix}_s${number}e${episode.episodeNumber}_still.jpg",
                                        )
                                    }
                                    ?.let {
                                        database.episodeDao().updateArtwork(episode.id, it)
                                        changed = true
                                    }
                            }
                    }
                OperationResult.Success(changed)
            }
            when (result) {
                is OperationResult.Success -> if (result.data) refreshed++
                is OperationResult.Failure -> {
                    failure = result.error
                    if (result.error == DataError.UNAUTHORIZED || result.error == DataError.OFFLINE)
                        return@safely result
                }
            }
        }
        val referenced =
            database
                .mediaDao()
                .getAll()
                .first()
                .flatMap { listOfNotNull(it.posterPath, it.backdropPath, it.logoPath) }
                .toMutableSet()
        referenced += database.episodeDao().getAll().mapNotNull { it.thumbnailPath }
        referenced += database.seasonDao().getAll().mapNotNull { it.posterPath }
        images.pruneOrphans(referenced)
        if (failure != null) OperationResult.Failure(failure)
        else OperationResult.Success(refreshed)
    }

    private suspend fun <T> optionalArtwork(block: suspend () -> T): T? =
        try {
            block()
        } catch (e: HttpException) {
            if (e.code() == 404) null else throw e
        }

    override suspend fun checkAvailability(): OperationResult<Set<Long>, DataError> = safely {
        val unavailable = mutableSetOf<Long>()
        val all = database.mediaDao().getAll().first()
        for (media in all) {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            val paths =
                media.filePath?.let { listOf(it) }
                    ?: database.episodeDao().getAllForShow(media.id).first().map { it.filePath }
            if (
                paths.isEmpty() ||
                    paths.any { path ->
                        try {
                            context.contentResolver.openFileDescriptor(Uri.parse(path), "r")?.use {
                                false
                            } ?: true
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            true
                        }
                    }
            )
                unavailable += media.id
        }
        OperationResult.Success(unavailable)
    }

    private suspend fun <T> safely(
        block: suspend () -> OperationResult<T, DataError>
    ): OperationResult<T, DataError> =
        withContext(Dispatchers.IO) {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpException) {
                OperationResult.Failure(
                    if (e.code() == 401) DataError.UNAUTHORIZED
                    else if (e.code() == 404) DataError.NOT_FOUND else DataError.SERVER
                )
            } catch (_: IOException) {
                OperationResult.Failure(DataError.OFFLINE)
            } catch (_: SecurityException) {
                OperationResult.Failure(DataError.PERMISSION)
            } catch (_: android.database.sqlite.SQLiteFullException) {
                OperationResult.Failure(DataError.STORAGE)
            } catch (_: Exception) {
                OperationResult.Failure(DataError.UNKNOWN)
            }
        }
}
