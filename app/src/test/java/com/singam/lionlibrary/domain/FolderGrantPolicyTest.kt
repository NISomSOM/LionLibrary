package com.singam.lionlibrary.domain

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.singam.lionlibrary.domain.model.FolderGrantPolicy
import com.singam.lionlibrary.domain.model.FolderTree
import org.junit.jupiter.api.Test

class FolderGrantPolicyTest {
    // Break vectors: the other root uses the same tree, a Shows root nested inside the old Movies
    // tree (friend's SD-card layout), a sibling whose id merely shares a prefix ("Movies2"), a
    // volume root ("primary:"), another provider with an identical id, opaque ids, unknown roots.
    private val authority = "com.android.externalstorage.documents"

    private fun tree(id: String, auth: String = authority) = FolderTree(auth, id)

    @Test
    fun `same tree in use keeps the grant`() {
        assertThat(
                FolderGrantPolicy.canRelease(tree("primary:Movies"), listOf(tree("primary:Movies")))
            )
            .isFalse()
    }

    @Test
    fun `root nested inside the old tree keeps the grant`() {
        assertThat(
                FolderGrantPolicy.canRelease(
                    tree("1A2B-3C4D:Media"),
                    listOf(tree("1A2B-3C4D:Media/Shows")),
                )
            )
            .isFalse()
        assertThat(FolderGrantPolicy.canRelease(tree("primary:"), listOf(tree("primary:Shows"))))
            .isFalse()
    }

    @Test
    fun `prefix sibling, parent of the old tree and other providers allow release`() {
        assertThat(
                FolderGrantPolicy.canRelease(
                    tree("primary:Movies"),
                    listOf(tree("primary:Movies2")),
                )
            )
            .isTrue()
        assertThat(
                FolderGrantPolicy.canRelease(
                    tree("primary:Media/Movies"),
                    listOf(tree("primary:Media")),
                )
            )
            .isTrue()
        assertThat(
                FolderGrantPolicy.canRelease(
                    tree("primary:Movies"),
                    listOf(tree("primary:Movies/Shows", auth = "other.provider")),
                )
            )
            .isTrue()
    }

    @Test
    fun `opaque ids and unconfigured roots allow release`() {
        assertThat(
                FolderGrantPolicy.canRelease(
                    tree("acc=1;doc=9", "cloud"),
                    listOf(tree("acc=1;doc=12", "cloud"), null),
                )
            )
            .isTrue()
        assertThat(FolderGrantPolicy.canRelease(tree("primary:Movies"), emptyList())).isTrue()
    }
}
