package com.example.mediahub.data.scanner

import com.example.mediahub.domain.model.MediaType

/**
 * Pure utility class for parsing media filenames into structured data.
 * No Android dependencies — fully testable with JUnit.
 */
class FileNameParser {

    // Movie patterns
    private val moviePatternParenYear = Regex("""^(.+?)\s*\((\d{4})\)""")
    private val moviePatternDotYear = Regex("""^(.+?)[\s.](\d{4})[\s.]""")

    // Episode patterns (handles S01E04, s2e15, 2x04, S01P02)
    private val seasonEpisodePattern = Regex("""[Ss](\d{1,2})[EePp](\d{1,2})""", RegexOption.IGNORE_CASE)
    private val scenePattern = Regex("""(\d{1,2})x(\d{2})""", RegexOption.IGNORE_CASE)
    
    // Anime absolute episode patterns
    private val absoluteEpisodeDash = Regex("""-\s*(\d{1,4})""")
    private val absoluteEpisodeFallback = Regex("""\s0*(\d{1,3})\s*$""")

    fun parse(scannedFile: ScannedFile): ParsedFile {
        val nameWithoutExtension = scannedFile.displayName.substringBeforeLast('.')

        return when (scannedFile.mediaType) {
            MediaType.MOVIE -> parseMovie(nameWithoutExtension)
            MediaType.TV_SHOW, MediaType.ANIME -> parseEpisode(nameWithoutExtension, scannedFile.parentFolderName)
        }
    }

    private fun parseMovie(name: String): ParsedFile {
        // Pattern 1: Title (Year) — e.g., "Interstellar (2014)"
        moviePatternParenYear.find(name)?.let { match ->
            val title = cleanTitle(match.groupValues[1])
            val year = match.groupValues[2].toIntOrNull()
            return ParsedFile.Movie(title, year)
        }

        // Pattern 2: Title.Year.tags — e.g., "Oppenheimer.2023.1080p"
        moviePatternDotYear.find(name)?.let { match ->
            val title = cleanTitle(match.groupValues[1])
            val year = match.groupValues[2].toIntOrNull()
            return ParsedFile.Movie(title, year)
        }

        // Pattern 3: Title only — e.g., "Interstellar"
        val title = cleanTitle(name)
        return if (title.isNotBlank()) {
            ParsedFile.Movie(title, null)
        } else {
            ParsedFile.Unknown(name)
        }
    }

    private fun parseEpisode(name: String, parentFolderName: String): ParsedFile {
        val title = cleanTitle(parentFolderName)
        if (title.isBlank()) return ParsedFile.Unknown(name)

        seasonEpisodePattern.find(name)?.let { match ->
            val season = match.groupValues[1].toIntOrNull() ?: 1
            val episode = match.groupValues[2].toIntOrNull() ?: 1
            return ParsedFile.Episode(title, season, episode)
        }

        scenePattern.find(name)?.let { match ->
            val season = match.groupValues[1].toIntOrNull() ?: 1
            val episode = match.groupValues[2].toIntOrNull() ?: 1
            return ParsedFile.Episode(title, season, episode)
        }

        // Strip bracket tags before absolute episode checks for safety
        val strippedName = name.replace(Regex("""^\[.+?]\s*"""), "").trim()

        absoluteEpisodeDash.find(strippedName)?.let { match ->
            val episode = match.groupValues[1].toIntOrNull() ?: 1
            return ParsedFile.Episode(title, 1, episode)
        }

        absoluteEpisodeFallback.find(strippedName)?.let { match ->
            val episode = match.groupValues[1].toIntOrNull() ?: 1
            return ParsedFile.Episode(title, 1, episode)
        }

        return ParsedFile.Unknown(name)
    }

    /**
     * Clean up a parsed title by replacing dots/underscores with spaces and trimming.
     */
    private fun cleanTitle(raw: String): String {
        return raw
            .replace('.', ' ')
            .replace('_', ' ')
            .trim()
    }
}
