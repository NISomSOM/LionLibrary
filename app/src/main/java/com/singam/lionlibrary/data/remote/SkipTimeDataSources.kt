package com.singam.lionlibrary.data.remote

import com.singam.lionlibrary.data.mapper.toSkipDbSeconds
import com.singam.lionlibrary.data.mapper.toSkipSegments
import com.singam.lionlibrary.data.remote.api.SkipDbApiService
import com.singam.lionlibrary.data.remote.api.TheIntroDbApiService
import com.singam.lionlibrary.data.remote.api.TmdbApiService
import com.singam.lionlibrary.domain.model.DataError
import com.singam.lionlibrary.domain.model.OperationResult
import com.singam.lionlibrary.domain.model.SkipLookupKey
import com.singam.lionlibrary.domain.model.SkipSegment
import com.singam.lionlibrary.domain.model.map
import com.singam.lionlibrary.domain.repository.SettingsRepository
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerializationException
import retrofit2.HttpException

interface TheIntroDbDataSource {
    suspend fun segments(
        key: SkipLookupKey,
        durationMs: Long,
    ): OperationResult<List<SkipSegment>, DataError>
}

interface SkipDbDataSource {
    suspend fun segments(
        imdbId: String,
        key: SkipLookupKey,
        durationMs: Long,
    ): OperationResult<List<SkipSegment>, DataError>
}

/** Success(null) means there is no usable IMDb id, including when no TMDB key is saved. */
interface ImdbIdDataSource {
    suspend fun imdbId(key: SkipLookupKey): OperationResult<String?, DataError>
}

class RetrofitTheIntroDbDataSource(
    private val api: TheIntroDbApiService,
    private val gate: ProviderRequestGate,
) : TheIntroDbDataSource {
    override suspend fun segments(key: SkipLookupKey, durationMs: Long) =
        gate
            .call { api.media(key.tmdbId, key.season, key.episode, durationMs) }
            .map { it?.toSkipSegments(durationMs).orEmpty() }
}

class RetrofitSkipDbDataSource(
    private val api: SkipDbApiService,
    private val gate: ProviderRequestGate,
) : SkipDbDataSource {
    override suspend fun segments(imdbId: String, key: SkipLookupKey, durationMs: Long) =
        gate
            .call {
                api.segments(imdbId, key.season, key.episode, durationMs.toSkipDbSeconds())
            }
            .map { it?.toSkipSegments(durationMs).orEmpty() }
}

/** Uses the user's TMDB key; movies use their own id, episodes the show-level IMDb id. */
class TmdbImdbIdDataSource(
    private val api: TmdbApiService,
    private val settings: SettingsRepository,
) : ImdbIdDataSource {
    override suspend fun imdbId(key: SkipLookupKey): OperationResult<String?, DataError> {
        val apiKey = settings.tmdbApiKey.first().trim()
        if (apiKey.isEmpty()) return OperationResult.Success(null)
        return try {
            val ids =
                if (key.isMovie) api.getMovieExternalIds(key.tmdbId, apiKey)
                else api.getTvExternalIds(key.tmdbId, apiKey)
            OperationResult.Success(ids.imdbId?.trim()?.takeIf { ImdbPattern.matches(it) })
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (http: HttpException) {
            when (http.code()) {
                401 -> OperationResult.Failure(DataError.UNAUTHORIZED)
                404 -> OperationResult.Success(null)
                else -> OperationResult.Failure(DataError.SERVER)
            }
        } catch (_: SerializationException) {
            OperationResult.Failure(DataError.UNKNOWN)
        } catch (_: IOException) {
            OperationResult.Failure(DataError.OFFLINE)
        }
    }

    private companion object {
        val ImdbPattern = Regex("^tt[0-9]{7,8}$")
    }
}
