package com.singam.lionlibrary.data.scanner

class FileNameParser {

    // Noise categories based on Guessit
    private val sitePrefixPattern = Regex("""^www\.[^\s-]+\.[a-z]{2,4}\s*-\s*""", RegexOption.IGNORE_CASE)

    // Applied after dot/underscore replacement
    private val resPattern = Regex("""\b(480|576|720|1080|2160|4320)p\b|\b4K\b|\bUHD\b""", RegexOption.IGNORE_CASE)
    private val sourcePattern = Regex("""\b(BluRay|BDRip|BRRip|BD|WEB[- ]?DL|WEB[- ]?Rip|HDTV|DVDRip|HDRip)\b""", RegexOption.IGNORE_CASE)
    private val codecPattern = Regex("""\b(x264|x265|H(?:[- .])?264|H(?:[- .])?265|HEVC|AVC|AV1|XviD)\b""", RegexOption.IGNORE_CASE)
    private val audioPattern = Regex("""\bDD\+|\b(DDP?5(?:[.\s])1|DD5(?:[.\s])1|AAC(?:[.\s])?\d?(?:[.\s])?\d?|FLAC|DTS[- ]?HD|TrueHD|Atmos|Opus|DTS|Dual[- ]?Audio|Multi[- ]?Audio|DD)\b""", RegexOption.IGNORE_CASE)
    private val hdrPattern = Regex("""\bHDR(10\+?)?\b|\bDV\b|\bDoVi\b|\bSDR\b""", RegexOption.IGNORE_CASE)
    private val editionPattern = Regex("""\b(Extended|Director'?s?\s?Cut|Unrated|Remastered|Theatrical|Uncut|Special\s?Edition|IMAX)\b""", RegexOption.IGNORE_CASE)
    private val properRepackPattern = Regex("""\b(PROPER|REPACK|INTERNAL|LIMITED)\b""", RegexOption.IGNORE_CASE)
    private val partPattern = Regex("""\bPart\s?\d\b|\bCD\d\b|\bDisc\s?\d\b""", RegexOption.IGNORE_CASE)
    private val trailingGroupPattern = Regex("""-[A-Za-z0-9]+$""")
    private val siteSuffixPattern = Regex("""[- ]?\b[A-Za-z0-9]+\s?Com\b""", RegexOption.IGNORE_CASE)
    private val langPattern = Regex("""\b(ENG|JPN|Hindi|Multi-?Sub|Hin|Eng)\b""", RegexOption.IGNORE_CASE)

    // Episode markers
    private val standardEpisodePattern = Regex("""\b(?:[Ss](\d{1,2}))?[EePp](\d{1,3})(?:-[EePp]?(\d{1,3}))?\b""", RegexOption.IGNORE_CASE)
    private val sceneEpisodePattern = Regex("""\b(\d{1,2})x(\d{2,3})(?:-(\d{2,3}))?\b""", RegexOption.IGNORE_CASE)
    private val absoluteDashPattern = Regex("""-\s*(\d{1,4})(?:v\d+)?\b""")
    private val absoluteTrailingPattern = Regex("""\s0*(\d{1,3})\s*$""")

    // Season markers
    private val seasonWordPattern = Regex("""\bSeason\s*(\d+)\b""", RegexOption.IGNORE_CASE)
    private val seasonSPattern = Regex("""\bS(\d{1,2})(?:P\d{1,2})?\b""", RegexOption.IGNORE_CASE)
    private val partSeasonPattern = Regex("""\bPart\s*(\d+)\b""", RegexOption.IGNORE_CASE)
    private val specialsPattern = Regex("""\bSpecials\b""", RegexOption.IGNORE_CASE)

    fun stripNoise(raw: String): String {
        var clean = raw
        
        // Globally strip all [ ] blocks
        clean = clean.replace(Regex("""\[.*?\]"""), " ")
        
        clean = sitePrefixPattern.replace(clean, "")
        
        // Remove stray (Part 1+2+3) etc.
        clean = clean.replace(Regex("""\(Part.*?\)""", RegexOption.IGNORE_CASE), " ")
        
        // Normalize separators (preserve dash for trailing group pattern)
        clean = clean.replace('.', ' ').replace('_', ' ')
        
        clean = resPattern.replace(clean, "")
        clean = sourcePattern.replace(clean, "")
        clean = codecPattern.replace(clean, "")
        clean = audioPattern.replace(clean, "")
        clean = hdrPattern.replace(clean, "")
        clean = editionPattern.replace(clean, "")
        clean = properRepackPattern.replace(clean, "")
        // clean = partPattern.replace(clean, "") // Commented out so it doesn't strip 'Part 6' from JoJo Part 6
        clean = siteSuffixPattern.replace(clean, "")
        clean = langPattern.replace(clean, "")
        
        // Explicitly strip noise commonly missed
        clean = clean.replace(Regex("""\b(10\s?bits?|NF|AMZN|WEB)\b""", RegexOption.IGNORE_CASE), "")
        
        // Remove trailing empty parens and standalone dashes
        clean = clean.replace(Regex("""\(\s*\)"""), "")
        clean = clean.replace(Regex("""-+$"""), "")
        
        clean = clean.trim()
        clean = clean.replace(trailingGroupPattern, "")
        
        // Normalize remaining dashes
        clean = clean.replace('-', ' ')
        
        return clean.replace(Regex("""\s+"""), " ").trim()
    }

    fun parseMovieTitle(raw: String): Pair<String, Int?> {
        val clean = stripNoise(raw)
        val yearRegex = Regex("""(?:^|\s)\(?((?:19|20)\d{2})\)?(?:\s|$)""")
        val matches = yearRegex.findAll(clean).toList()
        
        var year: Int? = null
        var title = clean
        
        if (matches.isNotEmpty()) {
            val lastMatch = matches.last()
            year = lastMatch.groupValues[1].toIntOrNull()
            title = clean.removeRange(lastMatch.range)
        }
        
        
        title = title.replace(Regex("""\(.*?\)"""), "").replace(Regex("""\s+"""), " ").trim()
        return Pair(title, year)
    }

    fun parseShowFolderIdentity(folderName: String): Pair<Int?, String> {
        var baseName = folderName
        standardEpisodePattern.find(baseName)?.let { baseName = baseName.substring(0, it.range.first) }
        if (baseName == folderName) {
            sceneEpisodePattern.find(baseName)?.let { baseName = baseName.substring(0, it.range.first) }
        }
        
        var clean = stripNoise(baseName)
        var season: Int? = null

        seasonWordPattern.find(clean)?.let {
            season = it.groupValues[1].toInt()
            clean = clean.removeRange(it.range)
        } ?: seasonSPattern.find(clean)?.let {
            season = it.groupValues[1].toInt()
            clean = clean.removeRange(it.range)
        } ?: partSeasonPattern.find(clean)?.let {
            season = it.groupValues[1].toInt()
            clean = clean.removeRange(it.range)
        } ?: specialsPattern.find(clean)?.let {
            season = 0
            clean = clean.removeRange(it.range)
        }
        
        clean = clean.replace(Regex("""\(.*?\)"""), "")
        clean = clean.replace(Regex("""\(\s*\)"""), "")
        clean = clean.replace(Regex("""-+$"""), "")
        clean = clean.replace(Regex("""\s+"""), " ").trim()
        
        return Pair(season, clean)
    }

    fun parseSeasonAndEpisodeNumbers(filename: String): Pair<Int?, List<Int>> {
        standardEpisodePattern.find(filename)?.let { match ->
            val seasonStr = match.groupValues[1]
            val start = match.groupValues[2].toInt()
            val endStr = match.groupValues[3]
            
            val season = seasonStr.toIntOrNull()
            val eps = if (endStr.isNotBlank()) (start..endStr.toInt()).toList() else listOf(start)
            return Pair(season, eps)
        }

        sceneEpisodePattern.find(filename)?.let { match ->
            val seasonStr = match.groupValues[1]
            val start = match.groupValues[2].toInt()
            val endStr = match.groupValues[3]
            
            val season = seasonStr.toIntOrNull()
            val eps = if (endStr.isNotBlank()) (start..endStr.toInt()).toList() else listOf(start)
            return Pair(season, eps)
        }

        // For absolute dash pattern, strip brackets first
        val strippedBracket = filename.replace(Regex("""\[.*?\]"""), "").trim()
        absoluteDashPattern.find(strippedBracket)?.let { match ->
            return Pair(null, listOf(match.groupValues[1].toInt()))
        }

        absoluteTrailingPattern.find(strippedBracket)?.let { match ->
            return Pair(null, listOf(match.groupValues[1].toInt()))
        }

        return Pair(null, emptyList())
    }
}
