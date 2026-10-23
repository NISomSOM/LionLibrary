package com.singam.lionlibrary.data.scanner

import assertk.assertThat
import assertk.assertions.*
import org.junit.jupiter.api.Test

class FileNameParserBoundaryTest {
    private val parser = FileNameParser()

    @Test
    fun `season overflow retains title without throwing`() {
        assertThat(parser.parseShowFolderIdentity("Show Season 99999999999999999999999"))
            .isEqualTo(null to "Show")
    }

    @Test
    fun `reversed range never creates an episode`() {
        assertThat(parser.parseSeasonAndEpisodeNumbers("Show S01E03-E01").second).isEmpty()
    }

    @Test
    fun `maximum recognized range is bounded`() {
        assertThat(parser.parseSeasonAndEpisodeNumbers("S01E001-E999").second.size).isEqualTo(999)
    }

    @Test
    fun `specials and ranges retain their source numbering`() {
        assertThat(parser.parseSeasonAndEpisodeNumbers("Show S00E01-E03.mkv"))
            .isEqualTo(0 to listOf(1, 2, 3))
    }

    @Test
    fun `extra title does not cut a unicode prefix that is only a partial word`() {
        assertThat(parser.parseExtraTitle("千と千尋たち Opening", "千と千尋")).isEqualTo("千と千尋たち Opening")
    }
}
