package com.singam.lionlibrary.data.scanner

import assertk.assertThat
import assertk.assertions.*
import com.singam.lionlibrary.data.remote.api.TmdbApiService
import com.singam.lionlibrary.data.remote.dto.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class TmdbMatcherTest {
    // Break vectors: remakes, duplicate IDs, original script, Romaji/English aliases,
    // API order vs text order and aliases for an already-confident wrong candidate.
    // Live break vectors: year bonus caps a same-year fuzzy sequel at the exact-title
    // score; optional alias transport failure must not lose a confident direct match.
    @Test
    fun `exact movie title beats fuzzy same year title after bonus saturation`() = runTest {
        val api =
            MatchApiFake(
                movies =
                    listOf(
                        MovieDto(1746767, "Michael Crunch", releaseDate = "2026-08-09"),
                        MovieDto(55587, "Michael", releaseDate = "1924-09-26"),
                        MovieDto(936075, "Michael", releaseDate = "2026-04-22"),
                    )
            )
        assertThat(TmdbMatcher(api).movie("Michael", 2026, "fixture")?.candidate?.id)
            .isEqualTo(936075)
    }

    @Test
    fun `optional alias network failure preserves strong direct movie evidence`() = runTest {
        val api =
            MatchApiFake(
                movies = listOf(MovieDto(1, "Example", releaseDate = "2026-01-01")),
                aliasFailure = java.io.IOException("fixture"),
            )
        assertThat(TmdbMatcher(api).movie("Example", 2026, "fixture")?.candidate?.id).isEqualTo(1)
    }

    @Test
    fun `movie year resolves remakes and duplicate results without TV lookup`() = runTest {
        val api =
            MatchApiFake(
                movies =
                    listOf(
                        MovieDto(55587, "Michael", releaseDate = "1924-09-26"),
                        MovieDto(936075, "Michael", releaseDate = "2026-04-22"),
                        MovieDto(936075, "Michael", releaseDate = "2026-04-22"),
                    )
            )
        assertThat(TmdbMatcher(api).movie("Michael", 2026, "fixture")?.candidate?.id)
            .isEqualTo(936075)
    }

    @Test
    fun `Romaji anime film matches alias on movie endpoint`() = runTest {
        val name = "Seishun Buta Yarou wa Randoseru Girl no Yume wo Minai"
        val api =
            MatchApiFake(
                movies = listOf(MovieDto(7, "Rascal Does Not Dream of a Knapsack Kid")),
                movieAliases = mapOf(7 to listOf(name)),
            )
        val parsed =
            FileNameParser()
                .parseMovieTitle(
                    "Seishun.Buta.Yarou.wa.Randoseru.Girl.no.Yume.wo.Minai.1080p.BDRip.10.bits.DTS.x265-EMBER"
                )
        assertThat(parsed.first).isEqualTo(name)
        assertThat(TmdbMatcher(api).movie(parsed.first, parsed.second, "fixture")?.candidate?.id)
            .isEqualTo(7)
    }

    @Test
    fun `exact original script and English aliases outrank fuzzy titles`() = runTest {
        val original = "千と千尋の神隠し"
        val movieApi =
            MatchApiFake(movies = listOf(MovieDto(8, "Spirited Away", originalTitle = original)))
        assertThat(TmdbMatcher(movieApi).movie(original, null, "fixture")?.candidate?.id)
            .isEqualTo(8)
        val showApi =
            MatchApiFake(
                shows =
                    listOf(
                        TvShowDto(1, "Grand Blues!"),
                        TvShowDto(2, "ぐらんぶる"),
                    ),
                showAliases = mapOf(2 to listOf("Grand Blue")),
            )
        assertThat(TmdbMatcher(showApi).show("Grand Blue", "fixture")?.candidate?.id).isEqualTo(2)
    }
}

private class MatchApiFake(
    val movies: List<MovieDto> = emptyList(),
    val shows: List<TvShowDto> = emptyList(),
    val movieAliases: Map<Int, List<String>> = emptyMap(),
    val showAliases: Map<Int, List<String>> = emptyMap(),
    val aliasFailure: java.io.IOException? = null,
) : TmdbApiService {
    override suspend fun searchMovie(key: String, query: String, year: Int?) =
        MovieSearchResponse(1, movies, movies.size, 1)

    override suspend fun searchTv(key: String, query: String): TvSearchResponse {
        check(movies.isEmpty()) { "Movie-folder file must never search TV" }
        return TvSearchResponse(1, shows, shows.size, 1)
    }

    override suspend fun getMovieAlternativeTitles(
        id: Int,
        key: String,
    ): AlternativeTitlesResponse {
        aliasFailure?.let { throw it }
        return AlternativeTitlesResponse(
            id,
            titles = movieAliases[id].orEmpty().map { AlternativeTitleDto(title = it) },
        )
    }

    override suspend fun getMovieExternalIds(id: Int, key: String) = error("unused")

    override suspend fun getTvExternalIds(id: Int, key: String) = error("unused")

    override suspend fun getTvAlternativeTitles(id: Int, key: String) =
        AlternativeTitlesResponse(
            id,
            results = showAliases[id].orEmpty().map { AlternativeTitleDto(title = it) },
        )

    override suspend fun validateKey(key: String) = error("unused")

    override suspend fun getMovieDetails(id: Int, key: String) = error("unused")

    override suspend fun getTvDetails(id: Int, key: String) = error("unused")

    override suspend fun getSeasonDetails(id: Int, season: Int, key: String) = error("unused")

    override suspend fun getMovieImages(id: Int, key: String) = error("unused")

    override suspend fun getTvImages(id: Int, key: String) = error("unused")
}
