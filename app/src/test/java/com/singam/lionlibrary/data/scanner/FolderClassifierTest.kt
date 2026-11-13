package com.singam.lionlibrary.data.scanner

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import org.junit.jupiter.api.Test

/**
 * Behaviour the old-vs-new parity suite cannot reach: rows a real provider can return that the
 * in-memory parity tree never does, listing failures, and the one-listing-per-folder cost.
 */
class FolderClassifierTest {
    private class Tree : FolderLister<String> {
        val children = mutableMapOf<String, MutableList<FolderEntry<String>>>()
        val listed = mutableListOf<String>()
        var failOn: String? = null
        val root = FolderEntry<String>(null, true, false, "root", "auth:root")

        fun dir(parent: String, name: String): String = add(parent, name, directory = true)

        fun file(parent: String, name: String?): String = add(parent, name, directory = false)

        fun add(
            parent: String,
            name: String?,
            directory: Boolean,
            isFile: Boolean = !directory,
        ): String {
            val uri = "$parent/${name ?: "<null>"}"
            children
                .getOrPut(parent) { mutableListOf() }
                .add(FolderEntry(name, directory, isFile, uri, "auth:$uri"))
            return uri
        }

        override fun children(parent: FolderEntry<String>): List<FolderEntry<String>> {
            listed += parent.uri
            if (parent.uri == failOn) throw FolderUnavailableException("unavailable")
            return children[parent.uri].orEmpty()
        }
    }

    private fun classifier(tree: Tree) = FolderClassifier(FileNameParser(), tree)

    @Test
    fun `first listed subtitle wins a basename tie and matching is case sensitive`() {
        val tree = Tree()
        tree.file("root", "Heat (1995).ass")
        tree.file("root", "Heat (1995).srt")
        tree.file("root", "Heat (1995).mkv")
        tree.file("root", "RONIN (1998).srt")
        tree.file("root", "Ronin (1998).mkv")

        val movies =
            classifier(tree).scanMovies(tree.root).map { it as ScannedCandidate.Movie<String> }

        assertThat(movies.map { it.subtitleUri }).containsExactly("root/Heat (1995).ass", null)
    }

    @Test
    fun `rows without a name or with no type are ignored instead of crashing`() {
        val tree = Tree()
        tree.file("root", null)
        tree.add("root", "Ghost (1990).mkv", directory = false, isFile = false)
        tree.file("root", "Alien (1979).mkv")
        val nameless = tree.dir("root", "placeholder")
        tree.children.getValue("root").let { rows ->
            rows[rows.lastIndex] = FolderEntry(null, true, false, nameless, "auth:$nameless")
        }
        tree.file(nameless, "Brazil (1985).mkv")

        val movies = classifier(tree).scanMovies(tree.root)

        // The nameless folder's single video falls back to its own file name.
        assertThat(movies.map { (it as ScannedCandidate.Movie<String>).title })
            .containsExactly("Alien", "Brazil")
    }

    @Test
    fun `a failing subfolder listing fails the scan instead of looking empty`() {
        val tree = Tree()
        val show = tree.dir("root", "Some Show")
        val season = tree.dir(show, "Season 1")
        tree.file(season, "Some Show - S01E01.mkv")
        tree.failOn = season

        assertFailure { classifier(tree).scanShows(tree.root) }
            .isInstanceOf(FolderUnavailableException::class)
        tree.failOn = "root"
        assertFailure { classifier(tree).scanMovies(tree.root) }
            .isInstanceOf(FolderUnavailableException::class)
    }

    @Test
    fun `an empty root is a successful empty scan`() {
        val tree = Tree()
        assertThat(classifier(tree).scanMovies(tree.root)).isEmpty()
        assertThat(classifier(tree).scanShows(tree.root)).isEmpty()
    }

    @Test
    fun `each folder is listed once however many videos and subtitles it holds`() {
        val tree = Tree()
        val show = tree.dir("root", "Long Show")
        val season = tree.dir(show, "Season 1")
        for (episode in 1..24) {
            val number = episode.toString().padStart(2, '0')
            tree.file(season, "Long Show - S01E$number.mkv")
            tree.file(season, "Long Show - S01E$number.srt")
        }

        val shows = classifier(tree).scanShows(tree.root)

        val episodes = (shows.single() as ScannedCandidate.Show<String>).seasons.getValue(1)
        assertThat(episodes.size).isEqualTo(24)
        assertThat(episodes.count { it.subtitleUri == it.uri.replace(".mkv", ".srt") })
            .isEqualTo(24)
        assertThat(tree.listed).containsExactly("root", show, season)
    }

    @Test
    fun `the shows folder nested in movies is skipped only by its document key`() {
        val tree = Tree()
        val shows = tree.dir("root", "Shows")
        val show = tree.dir(shows, "Some Show")
        tree.file(show, "Some Show - S01E01.mkv")
        val lookalike = tree.dir("root", "Shows (1999)")
        tree.file(lookalike, "Shows (1999).mkv")

        val movies = classifier(tree).scanMovies(tree.root, excludeKey = "auth:$shows")

        assertThat(movies.map { (it as ScannedCandidate.Movie<String>).sourceUri })
            .containsExactly("$lookalike/Shows (1999).mkv")
        assertThat(tree.listed.contains(shows)).isEqualTo(false)
    }
}
