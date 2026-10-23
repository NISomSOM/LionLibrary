package com.singam.lionlibrary.data.scanner

import com.singam.lionlibrary.data.remote.api.TmdbApiService
import com.singam.lionlibrary.data.remote.dto.MovieDto
import com.singam.lionlibrary.data.remote.dto.TvShowDto
import com.singam.lionlibrary.domain.model.MatchScore
import com.singam.lionlibrary.domain.model.selectConfidentMatch
import com.singam.lionlibrary.util.ConfidenceScorer
import com.singam.lionlibrary.util.Constants
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import retrofit2.HttpException

/** Movie-folder files always use the movie endpoint, including anime films. */
class TmdbMatcher(private val api: TmdbApiService) {
    suspend fun movie(title: String, year: Int?, key: String): MatchScore<MovieDto>? {
        val first = retry { api.searchMovie(key, title, year) }.results
        val results =
            (if (first.isEmpty() && year != null) retry { api.searchMovie(key, title) }.results
                else first)
                .distinctBy { it.id }
        val scores = results.map { item ->
            val releaseYear = item.releaseDate?.take(4)?.toIntOrNull()
            MatchScore(
                item,
                score(title, listOfNotNull(item.title, item.originalTitle), year, releaseYear),
                year != null && year == releaseYear,
                score(title, listOfNotNull(item.title, item.originalTitle)),
            )
        }
        // Romaji aliases may have no resemblance to the English title. Include API relevance
        // as well as text similarity, and inspect aliases even for already-confident candidates.
        val shortlist =
            (scores.take(3) + scores.sortedByDescending { it.confidence }.take(3)).distinctBy {
                it.candidate.id
            }
        val candidates = shortlist.map { item ->
            val aliases = optional {
                retry { api.getMovieAlternativeTitles(item.candidate.id, key) }
            }
            val names = (aliases?.titles ?: aliases?.results).orEmpty().map { it.title }
            item.copy(
                confidence =
                    maxOf(
                        item.confidence,
                        score(
                            title,
                            names,
                            year,
                            item.candidate.releaseDate?.take(4)?.toIntOrNull(),
                        ),
                    ),
                titleSimilarity = maxOf(item.titleSimilarity, score(title, names)),
            )
        }
        return selectConfidentMatch(candidates, Constants.MATCH_CONFIDENCE_THRESHOLD)
    }

    suspend fun show(title: String, key: String): MatchScore<TvShowDto>? {
        val scores = retry {
            api.searchTv(key, title)
        }
            .results
            .distinctBy { it.id }
            .map { item ->
                MatchScore(item, score(title, listOfNotNull(item.name, item.originalName)))
            }
        val shortlist =
            (scores.take(3) + scores.sortedByDescending { it.confidence }.take(3)).distinctBy {
                it.candidate.id
            }
        val candidates = shortlist.map { item ->
            val aliases = optional { retry { api.getTvAlternativeTitles(item.candidate.id, key) } }
            val confidence =
                maxOf(
                    item.confidence,
                    score(
                        title,
                        (aliases?.titles ?: aliases?.results).orEmpty().map { it.title },
                    ),
                )
            item.copy(confidence = confidence, titleSimilarity = confidence)
        }
        return selectConfidentMatch(candidates, Constants.MATCH_CONFIDENCE_THRESHOLD)
    }

    private fun score(
        title: String,
        names: List<String>,
        year: Int? = null,
        releaseYear: Int? = null,
    ) =
        names
            .filter { it.isNotBlank() }
            .maxOfOrNull {
                ConfidenceScorer.computeConfidence(title, it, year, releaseYear)
            } ?: 0f

    private suspend fun <T> optional(block: suspend () -> T): T? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            if (e.code() == 401 || e.code() == 429 || e.code() >= 500) throw e else null
        } catch (_: IOException) {
            // Optional aliases must not discard strong primary/original-title evidence.
            null
        }

    private suspend fun <T> retry(block: suspend () -> T): T {
        var backoff = 2_000L
        repeat(4) {
            try {
                return block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpException) {
                if (e.code() != 429 && e.code() < 500) throw e
            } catch (_: IOException) {}
            delay(backoff)
            backoff *= 2
        }
        return block()
    }
}
