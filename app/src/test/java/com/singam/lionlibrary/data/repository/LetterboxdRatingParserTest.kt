package com.singam.lionlibrary.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LetterboxdRatingParserTest {

    @Test
    fun parsesTwitterMetaTag() {
        val html =
            """<head><meta name="twitter:label2" content="Average rating" />
            <meta name="twitter:data2" content="3.85 out of 5" /></head>"""
        assertEquals(3.85f, parseLetterboxdRating(html)!!, 0.001f)
    }

    @Test
    fun parsesWholeNumberRating() {
        assertEquals(
            4f,
            parseLetterboxdRating("""<meta name="twitter:data2" content="4 out of 5">""")!!,
            0.001f,
        )
    }

    @Test
    fun fallsBackToJsonLd() {
        val html =
            """<script type="application/ld+json">{"aggregateRating":{"@type":"AggregateRating","ratingValue":4.21,"ratingCount":12345}}</script>"""
        assertEquals(4.21f, parseLetterboxdRating(html)!!, 0.001f)
    }

    @Test
    fun returnsNullWithoutRating() {
        assertNull(parseLetterboxdRating("<html><head><title>Film</title></head></html>"))
        // A film with too few ratings has no average.
        assertNull(
            parseLetterboxdRating("""<meta name="twitter:data2" content="Not enough ratings">""")
        )
    }

    @Test
    fun rejectsOutOfRangeValues() {
        assertNull(parseLetterboxdRating(""""ratingValue": 87"""))
    }
}
