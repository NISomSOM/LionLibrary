package com.singam.lionlibrary.domain

import assertk.assertThat
import assertk.assertions.*
import com.singam.lionlibrary.domain.model.*
import org.junit.jupiter.api.Test

class DeletionSafetyTest {
    private fun node(
        id: String,
        parent: String?,
        directory: Boolean = true,
        video: Boolean = false,
        deletable: Boolean = true,
    ) = StorageNode(id, parent, "日本語 🦁 $id", directory, deletable, 1, 1, video)

    private fun tree(vararg nodes: StorageNode) =
        StorageTree("provider", "root", nodes.associateBy { it.id })

    private val root = node("root", null)
    private val show = node("uuid-folder", "root")
    private val season = node("season", show.id)
    private val episode = node("opaque-42", season.id, directory = false, video = true)

    @Test
    fun `season and wrapper ancestry selects only outer show folder with opaque ids`() {
        val storage = tree(root, show, season, episode)
        assertThat(DeletionSafety.showFolder(storage, setOf(episode.id), emptySet()))
            .isEqualTo(show)
    }

    @Test
    fun `loose episode cannot authorize deletion of Shows root`() {
        assertThat(
                DeletionSafety.showFolder(
                    tree(root, episode.copy(parentId = root.id)),
                    setOf(episode.id),
                    emptySet(),
                )
            )
            .isNull()
    }

    @Test
    fun `a second indexed show or movie blocks shared folder deletion`() {
        val foreign = node("foreign", show.id, false, true)
        assertThat(
                DeletionSafety.showFolder(
                    tree(root, show, season, episode, foreign),
                    setOf(episode.id),
                    setOf(foreign.id),
                )
            )
            .isNull()
    }

    @Test
    fun `new unscanned video blocks whole folder deletion`() {
        val unscanned = node("unscanned", show.id, false, true)
        assertThat(
                DeletionSafety.showFolder(
                    tree(root, show, season, episode, unscanned),
                    setOf(episode.id),
                    emptySet(),
                )
            )
            .isNull()
    }

    @Test
    fun `siblings with prefix collision do not belong to this folder`() {
        val sibling = node("uuid-folder-two", root.id)
        val other = node("other", sibling.id, false, true)
        assertThat(
                DeletionSafety.showFolder(
                    tree(root, show, season, episode, sibling, other),
                    setOf(episode.id),
                    setOf(other.id),
                )
            )
            .isEqualTo(show)
    }

    @Test
    fun `show spread over sibling season folders is ambiguous`() {
        val another = node("season-other", root.id)
        val second = node("second", another.id, false, true)
        assertThat(
                DeletionSafety.showFolder(
                    tree(root, show, season, episode, another, second),
                    setOf(episode.id, second.id),
                    emptySet(),
                )
            )
            .isNull()
    }

    @Test
    fun `missing episode or empty show cannot authorize folder deletion`() {
        val storage = tree(root, show, season)
        assertThat(DeletionSafety.showFolder(storage, setOf(episode.id), emptySet())).isNull()
        assertThat(DeletionSafety.showFolder(storage, emptySet(), emptySet())).isNull()
    }

    @Test
    fun `read only folders cannot be deleted`() {
        assertThat(
                DeletionSafety.showFolder(
                    tree(root, show.copy(deletable = false), season, episode),
                    setOf(episode.id),
                    emptySet(),
                )
            )
            .isNull()
    }

    @Test
    fun `cyclic provider ancestry terminates safely`() {
        val storage = tree(root, show.copy(parentId = "season"), season, episode)
        assertThat(DeletionSafety.showFolder(storage, setOf(episode.id), emptySet())).isNull()
    }

    @Test
    fun `subtitle and poster contents are included but never sibling folder contents`() {
        val subtitle = node("sub", season.id, false)
        val storage = tree(root, show, season, episode, subtitle)
        assertThat(storage.subtree(show.id))
            .containsExactlyInAnyOrder(show.id, season.id, episode.id, subtitle.id)
    }
}
