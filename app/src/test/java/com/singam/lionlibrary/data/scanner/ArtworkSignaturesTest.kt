package com.singam.lionlibrary.data.scanner

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

/**
 * Pure encode/decode only (no disk). Break vectors: a damaged index must never mark a file as
 * trusted (truncated line, non-numeric fields, zero length, missing path), paths with spaces or
 * unicode survive, negative/zero modification times are kept exactly, and order is stable.
 */
class ArtworkSignaturesTest {
    @Test
    fun `round trip keeps every field and path exactly`() {
        val values =
            mapOf(
                "/data/files/posters/movie_1_poster.jpg" to
                    ArtworkSignature(1234, 1_700_000_000_000),
                "/data/files/posters/tv 9 ショー 😀.png" to ArtworkSignature(1, 0),
                "/data/files/posters/old.jpg" to ArtworkSignature(Long.MAX_VALUE, -1),
            )

        assertThat(ArtworkSignatures.decode(ArtworkSignatures.encode(values))).isEqualTo(values)
    }

    @Test
    fun `damaged lines are dropped, never trusted`() {
        val text =
            listOf(
                    "12\t34",
                    "x\t34\t/a.jpg",
                    "12\ty\t/b.jpg",
                    "0\t34\t/zero.jpg",
                    "-5\t34\t/negative.jpg",
                    "12\t34\t",
                    "",
                )
                .joinToString("\n")

        assertThat(ArtworkSignatures.decode(text)).isEmpty()
    }

    @Test
    fun `encoding is sorted by path so rewrites are stable`() {
        val encoded =
            ArtworkSignatures.encode(
                mapOf("/b" to ArtworkSignature(2, 2), "/a" to ArtworkSignature(1, 1))
            )

        assertThat(encoded).isEqualTo("1\t1\t/a\n2\t2\t/b\n")
    }
}
