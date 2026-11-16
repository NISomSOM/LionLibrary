package com.singam.lionlibrary.data.repository

import android.database.sqlite.SQLiteFullException
import com.singam.lionlibrary.data.local.db.MediaDatabase
import com.singam.lionlibrary.data.remote.api.TmdbApiService
import com.singam.lionlibrary.data.scanner.ImageCacheManager
import com.singam.lionlibrary.domain.model.*
import com.singam.lionlibrary.domain.repository.*
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import retrofit2.HttpException

class TmdbMetadataRepository(
    private val api: TmdbApiService,
    private val database: MediaDatabase,
    private val settings: SettingsRepository,
    private val images: ImageCacheManager,
) : MetadataRepository {
    override suspend fun validateKey(key: String): OperationResult<Unit, DataError> = safely {
        if (key.isBlank()) return@safely OperationResult.Failure(DataError.UNAUTHORIZED)
        api.validateKey(key)
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
            currentCoroutineContext().ensureActive()
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
                val missingLanguage = item.originalLanguage == null
                if (
                    !missingLanguage &&
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
                if (missingLanguage && !missingPoster && !missingBackdrop) {
                    val language =
                        if (movie) api.getMovieDetails(id, key).originalLanguage
                        else api.getTvDetails(id, key).originalLanguage
                    database.mediaDao().updateOriginalLanguage(item.id, language.orEmpty())
                }
                if (missingPoster || missingBackdrop) {
                    val (paths, language) =
                        if (movie)
                            api.getMovieDetails(id, key).let {
                                (it.posterPath to it.backdropPath) to it.originalLanguage
                            }
                        else
                            api.getTvDetails(id, key).let {
                                (it.posterPath to it.backdropPath) to it.originalLanguage
                            }
                    if (missingLanguage)
                        database.mediaDao().updateOriginalLanguage(item.id, language.orEmpty())
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
        images.persistValidation()
        if (failure != null) OperationResult.Failure(failure)
        else OperationResult.Success(refreshed)
    }

    private suspend fun <T> optionalArtwork(block: suspend () -> T): T? =
        try {
            block()
        } catch (e: HttpException) {
            if (e.code() == 404) null else throw e
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
            } catch (_: SQLiteFullException) {
                OperationResult.Failure(DataError.STORAGE)
            } catch (_: Exception) {
                OperationResult.Failure(DataError.UNKNOWN)
            }
        }
}
