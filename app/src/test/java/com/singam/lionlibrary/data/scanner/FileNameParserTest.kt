package com.singam.lionlibrary.data.scanner

import org.junit.Assert.assertEquals
import org.junit.Test

class FileNameParserTest {
    private val parser = FileNameParser()

    @Test
    fun testParseShowFolderIdentity() {
        val (season1, identity1) = parser.parseShowFolderIdentity("Gen V S1 - [Group1]")
        assertEquals(1, season1)
        assertEquals("Gen V", identity1)

        val (season2, identity2) = parser.parseShowFolderIdentity("Gen-V S2 [Group2]")
        assertEquals(2, season2)
        assertEquals("Gen V", identity2)
        
        val (season3, identity3) = parser.parseShowFolderIdentity("Seishun Buta Yarou wa Randoseru Girl no Yume wo Minai")
        assertEquals(null, season3)
        assertEquals("Seishun Buta Yarou wa Randoseru Girl no Yume wo Minai", identity3)
        
        val (season4, identity4) = parser.parseShowFolderIdentity("[Anime Time] JoJo's Bizarre Adventure Part 6 - Stone Ocean (Part 1+2+3) [NF][Dual Audio] [1080p][HEVC 10bit x265][Multi Sub] [Batch]")
        assertEquals(6, season4) // Validate extraction from "Part 6"
        assertEquals("JoJo's Bizarre Adventure Stone Ocean", identity4)
        
        val (season5, identity5) = parser.parseShowFolderIdentity("Dandadan S02 1080p Dual Audio WEBRip DD+ x265-EMBER")
        assertEquals(2, season5)
        assertEquals("Dandadan", identity5)
    }

    @Test
    fun testParseMovieTitle() {
        val (title1, year1) = parser.parseMovieTitle("Oppenheimer.2023.1080p.BluRay.x264")
        assertEquals("Oppenheimer", title1)
        assertEquals(2023, year1)
        
        val (title2, year2) = parser.parseMovieTitle("The Matrix (1999) [1080p]")
        assertEquals("The Matrix", title2)
        assertEquals(1999, year2)
        
        val (title3, year3) = parser.parseMovieTitle("Michael (2026) [1080p] [WEBRip] [5.1] [YTS.BZ]")
        assertEquals("Michael", title3)
        assertEquals(2026, year3)
    }

    @Test
    fun testParseSeasonAndEpisodeNumbers() {
        val (season1, eps1) = parser.parseSeasonAndEpisodeNumbers("Gen V S01E03.mkv")
        assertEquals(1, season1)
        assertEquals(listOf(3), eps1)

        val (season2, eps2) = parser.parseSeasonAndEpisodeNumbers("Show - 14v2.mkv")
        assertEquals(null, season2)
        assertEquals(listOf(14), eps2)

        val (season3, eps3) = parser.parseSeasonAndEpisodeNumbers("[SubsPlease] Show - 24 (1080p) [F3A1C2D4].mkv")
        assertEquals(null, season3)
        assertEquals(listOf(24), eps3)
        
        val (season4, eps4) = parser.parseSeasonAndEpisodeNumbers("Show S03E01-E03.mkv")
        assertEquals(3, season4)
        assertEquals(listOf(1, 2, 3), eps4)
    }
}
