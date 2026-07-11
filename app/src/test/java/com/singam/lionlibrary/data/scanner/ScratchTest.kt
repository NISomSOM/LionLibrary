package com.singam.lionlibrary.data.scanner

import org.junit.Test
import org.junit.Assert.assertEquals

class ScratchTest {
    @Test
    fun testParse() {
        val parser = FileNameParser()
        val s = "[Judas] Frieren - S02E03v2.mkv"
        val (season, eps) = parser.parseSeasonAndEpisodeNumbers(s)
        println("OUTPUT: season=$season eps=$eps")
    }
}
