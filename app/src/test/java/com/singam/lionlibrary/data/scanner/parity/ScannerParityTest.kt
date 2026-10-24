package com.singam.lionlibrary.data.scanner.parity

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEmpty
import com.singam.lionlibrary.data.scanner.FileNameParser
import java.io.File
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

/**
 * Old-vs-new parity for the folder traversal rewrite. Both scanners get the same tree and must
 * produce identical candidate lists, in the same order, down to every URI and subtitle match.
 *
 * Until the rewrite lands, [candidate] is the legacy scanner itself, so this suite pins the
 * fixtures and writes the "before" snapshots. The rewrite swaps [candidate] for the new code.
 *
 * Break vectors: subtitle ties (.srt vs .ass with one basename), uppercase extensions, files with
 * no extension, blank parsed titles, collection folders at and past the depth limit, the Shows
 * folder nested inside Movies, season vs loose vs Specials precedence, extras numbering, wrapper
 * folders at and past their depth limit, split show folders merging, multi-episode files, root
 * videos in Shows, Unicode and bracket-heavy names, and empty folders.
 */
class ScannerParityTest {
    private val parser = FileNameParser()
    private val legacy = LegacyFolderScanner(parser)

    private interface Scanner {
        fun movies(root: ScanNode, excludeKey: String?): List<ParityCandidate>

        fun shows(root: ScanNode): List<ParityCandidate>
    }

    // Replace with the rewritten scanner when it exists.
    private val candidate: Scanner =
        object : Scanner {
            override fun movies(root: ScanNode, excludeKey: String?) =
                legacy.scanMoviesFolder(root, excludeKey)

            override fun shows(root: ScanNode) = legacy.scanShowsFolder(root)
        }

    private fun assertMoviesMatch(tree: ScanTreeBuilder, excludeKey: String? = null): String {
        val before = legacy.scanMoviesFolder(tree.root, excludeKey).render()
        assertThat(candidate.movies(tree.root, excludeKey).render()).isEqualTo(before)
        return before
    }

    private fun assertShowsMatch(tree: ScanTreeBuilder): String {
        val before = legacy.scanShowsFolder(tree.root).render()
        assertThat(candidate.shows(tree.root).render()).isEqualTo(before)
        return before
    }

    @Test
    fun `movie layouts match`() {
        val tree =
            ScanTreeBuilder("primary:Movies/Movies").apply {
                file("Inception (2010).mkv")
                file("Inception (2010).ass")
                file("Inception (2010).srt")
                file("LOUD.MOVIE.2001.MKV")
                file("noextension")
                file("1080p.mkv")
                file("notes.txt")
                file("Spirited Away (2001)/Spirited Away (2001).mkv")
                file("Spirited Away (2001)/Spirited Away (2001).srt")
                file("1080p/Perfect Blue 1997.mp4")
                file("Double Feature/Alien 1979.mkv")
                file("Double Feature/Aliens 1986.mkv")
                file("Double Feature/Aliens 1986.vtt")
                file("Ghibli/Early/Nausicaa 1984/Nausicaa 1984.mkv")
                file("A/B/C/D/Too Deep 2000/Too Deep 2000.mkv")
                file("[Group] 千と千尋 (2001) [1080p]/[Group] 千と千尋 (2001) [1080p].mkv")
                dir("Empty")
                file("Shows/Some Show/Some Show - S01E01.mkv")
            }
        val excluded = tree.dir("Shows").documentKey
        val snapshot = assertMoviesMatch(tree, excluded)
        assertThat(snapshot).isNotEmpty()
        assertMoviesMatch(tree, excludeKey = null)
    }

    @Test
    fun `show layouts match`() {
        val tree =
            ScanTreeBuilder("primary:Movies/Shows").apply {
                file("Loose Root Episode S01E01.mkv")
                file("Seasoned/Season 1/Seasoned - S01E01.mkv")
                file("Seasoned/Season 1/Seasoned - S01E01.srt")
                file("Seasoned/Season 1/Seasoned - S01E02-E03.mkv")
                file("Seasoned/S2/Seasoned - S02E01.MKV")
                file("Seasoned/Specials/Seasoned - S00E01.mkv")
                file("Seasoned/Ignored Loose - S01E09.mkv")
                file("Seasoned/Extras/Making Of.mkv")
                file("Seasoned/NCOP/Seasoned NCOP 1.mkv")
                file("Seasoned/NCOP/Seasoned NCOP 1.ass")
                file("Specials Only/Specials/Specials Only - S00E01.mkv")
                file("Specials Only/Specials Only - 01.mkv")
                file("Specials Only/Specials Only - 02.mkv")
                file("Wrapped/Wrapped/Wrapped - S01E01.mkv")
                file("Wrapped/Wrapped/Wrapped - S01E02.mkv")
                file("Too Wrapped/a/b/Too Wrapped - S01E01.mkv")
                file("Split Show S02/Split Show - 01.mkv")
                file("Split Show/Split Show - S01E01.mkv")
                file("[Judas] ジョジョ (Season 06) [1080p][HEVC]/[Judas] ジョジョ - S06E05.mkv")
                file("[Judas] ジョジョ (Season 06) [1080p][HEVC]/[DKB] ジョジョ - S06E05 [9C6CF81C].mkv")
                dir("Empty Show")
                file("[1080p]/x.mkv")
            }
        assertThat(assertShowsMatch(tree)).isNotEmpty()
    }

    /**
     * Real layouts from References/LIBRARY_FOLDER_STRUCTURES.md (owner's phone and a friend's SD
     * card). References/ is git-ignored, so this skips on machines without it. The "before"
     * snapshots are written to build/scanner-parity for review.
     */
    @Test
    fun `real library layouts match`() {
        val repo =
            generateSequence(File("").absoluteFile) { it.parentFile }
                .firstOrNull { File(it, "References/LIBRARY_FOLDER_STRUCTURES.md").exists() }
        assumeTrue(repo != null, "No References/LIBRARY_FOLDER_STRUCTURES.md; skipping")
        val trees =
            ScanTreeBuilder.fromReference(File(repo, "References/LIBRARY_FOLDER_STRUCTURES.md"))
        val out = File(repo, "build/scanner-parity").apply { mkdirs() }

        val ownerMovies = trees.getValue("owner-movies")
        val ownerShows = trees.getValue("owner-shows")
        File(out, "legacy-owner-movies.txt")
            .writeText(assertMoviesMatch(ownerMovies), Charsets.UTF_8)
        File(out, "legacy-owner-shows.txt").writeText(assertShowsMatch(ownerShows), Charsets.UTF_8)

        // The friend's Shows folder lives inside the Movies folder, so the movie scan skips it.
        val binge = trees.getValue("friend-binge")
        val showsFolder = binge.dir("Shows")
        val bingeMovies = assertMoviesMatch(binge, excludeKey = showsFolder.documentKey)
        val bingeShows = legacy.scanShowsFolder(showsFolder).render()
        assertThat(candidate.shows(showsFolder).render()).isEqualTo(bingeShows)
        File(out, "legacy-friend-movies.txt").writeText(bingeMovies, Charsets.UTF_8)
        File(out, "legacy-friend-shows.txt").writeText(bingeShows, Charsets.UTF_8)
    }
}
