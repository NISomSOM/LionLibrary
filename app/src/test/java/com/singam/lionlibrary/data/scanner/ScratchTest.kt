package com.singam.lionlibrary.data.scanner

import org.junit.Test
import org.junit.Assert.assertEquals

class ScratchTest {
    @Test
    fun testTrailingGroup() {
        val parser = FileNameParser()
        val s = "Spider-Man (2002) 1080p BluRay x264-GROUPNAME"
        val (title, year) = parser.parseMovieTitle(s)
        println("OUTPUT: " + title)
        assertEquals("Spider Man", title) // Or Spider-Man if we don't strip dashes
    }

    @Test
    fun testJusticeLeague() {
        val parser = FileNameParser()
        val s = "Justice League (2001).mkv"
        val (title, year) = parser.parseMovieTitle(s)
        println("OUTPUT2: " + title)
        assertEquals("Justice League", title)
    }
}
