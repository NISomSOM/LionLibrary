package com.singam.lionlibrary.data.scanner.parity

import com.singam.lionlibrary.data.scanner.FileNameParser
import com.singam.lionlibrary.domain.model.MediaType
import com.singam.lionlibrary.util.Constants

/**
 * Frozen reference copy of FolderScanner as of commit ffe6a60, before the traversal rewrite.
 *
 * The only edits are mechanical type swaps so it runs on the JVM: DocumentFile -> ScanNode, Uri ->
 * String, MediaCandidate -> ParityCandidate, and documentKey() reading the node's key. The
 * classification logic must stay byte-for-byte equivalent; do not "fix" anything here.
 */
class LegacyFolderScanner(private val parser: FileNameParser) {

    private val seasonPattern1 = Regex("""(?:^|\s)Season\s*(\d+)""", RegexOption.IGNORE_CASE)
    private val seasonPattern2 = Regex("""(?:^|\s)S(\d+)(?:\s|$)""", RegexOption.IGNORE_CASE)
    private val specialsPattern = Regex("""^Specials?$""", RegexOption.IGNORE_CASE)

    private val extrasFolderNames =
        setOf(
            "trailers",
            "other",
            "others",
            "extras",
            "extra",
            "featurettes",
            "behind the scenes",
            "deleted scenes",
            "interviews",
            "scenes",
            "shorts",
            "bonus",
            "nc",
            "ncop",
            "nced",
            "creditless",
        )

    fun scanMoviesFolder(root: ScanNode, excludeKey: String? = null): List<ParityCandidate> {
        val results = mutableListOf<ParityCandidate>()

        val allFiles = root.listFiles()
        val subtitles = allFiles.filter { it.isFile && isSubtitleFile(it.name) }

        for (entry in allFiles) {
            if (entry.isFile && isVideoFile(entry.name)) {
                val nameWithoutExt = entry.name.substringBeforeLast('.')
                val subtitleUri =
                    subtitles.find { it.name.substringBeforeLast('.') == nameWithoutExt }?.uri
                val (title, year) = parser.parseMovieTitle(nameWithoutExt)
                if (title.isNotBlank()) {
                    results.add(ParityCandidate.Movie(entry.uri, title, year, subtitleUri))
                } else {
                    results.add(
                        ParityCandidate.Unknown(
                            entry.uri,
                            entry.name,
                            "Blank title",
                            MediaType.MOVIE,
                        )
                    )
                }
            } else if (entry.isDirectory) {
                scanMovieDirectory(entry, 1, excludeKey, results)
            }
        }
        return results
    }

    private fun scanMovieDirectory(
        dir: ScanNode,
        depth: Int,
        excludeKey: String?,
        results: MutableList<ParityCandidate>,
    ) {
        if (excludeKey != null && dir.documentKey == excludeKey) return

        val dirFiles = dir.listFiles()
        val videos = dirFiles.filter { it.isFile && isVideoFile(it.name) }
        val dirSubtitles = dirFiles.filter { it.isFile && isSubtitleFile(it.name) }
        if (videos.size == 1) {
            val video = videos.first()
            val nameWithoutExt = video.name.substringBeforeLast('.')
            val subtitleUri =
                dirSubtitles.find { it.name.substringBeforeLast('.') == nameWithoutExt }?.uri
            var (title, year) = parser.parseMovieTitle(dir.name)
            if (title.isBlank()) {
                val fallback = parser.parseMovieTitle(nameWithoutExt)
                title = fallback.first
                year = fallback.second
            }
            if (title.isNotBlank()) {
                results.add(ParityCandidate.Movie(video.uri, title, year, subtitleUri))
            } else {
                results.add(
                    ParityCandidate.Unknown(video.uri, video.name, "Blank title", MediaType.MOVIE)
                )
            }
        } else if (videos.size > 1) {
            for (video in videos) {
                val nameWithoutExt = video.name.substringBeforeLast('.')
                val subtitleUri =
                    dirSubtitles.find { it.name.substringBeforeLast('.') == nameWithoutExt }?.uri
                val (title, year) = parser.parseMovieTitle(nameWithoutExt)
                if (title.isNotBlank()) {
                    results.add(ParityCandidate.Movie(video.uri, title, year, subtitleUri))
                } else {
                    results.add(
                        ParityCandidate.Unknown(
                            video.uri,
                            video.name,
                            "Blank title",
                            MediaType.MOVIE,
                        )
                    )
                }
            }
        } else if (depth < Constants.MAX_MOVIE_FOLDER_DEPTH) {
            for (sub in dirFiles) {
                if (sub.isDirectory) scanMovieDirectory(sub, depth + 1, excludeKey, results)
            }
        }
    }

    fun scanShowsFolder(root: ScanNode): List<ParityCandidate> {
        val rawCandidates = mutableListOf<ParityCandidate.Show>()
        val results = mutableListOf<ParityCandidate>()

        for (entry in root.listFiles()) {
            if (entry.isDirectory) {
                val (embeddedSeason, identity) = parser.parseShowFolderIdentity(entry.name)
                if (identity.isBlank()) continue

                val seasonsMap = mutableMapOf<Int, MutableList<ParityEpisode>>()
                collectShowEpisodes(entry, identity, embeddedSeason, seasonsMap, 1)

                if (seasonsMap.isNotEmpty()) {
                    rawCandidates.add(ParityCandidate.Show(identity, seasonsMap))
                }
            } else if (entry.isFile && isVideoFile(entry.name)) {
                results.add(
                    ParityCandidate.Unknown(
                        entry.uri,
                        entry.name,
                        "Place inside a folder named after the show",
                        MediaType.TV_SHOW,
                    )
                )
            }
        }

        val grouped = rawCandidates.groupBy { it.title }
        val mergedCandidates = grouped.map { (identity, candidates) ->
            val mergedSeasons = mutableMapOf<Int, MutableList<ParityEpisode>>()
            for (candidate in candidates) {
                for ((season, eps) in candidate.seasons) {
                    mergedSeasons.getOrPut(season) { mutableListOf() }.addAll(eps)
                }
            }
            mergedSeasons[0]?.let { numberExtras(it) }
            ParityCandidate.Show(
                identity,
                mergedSeasons.mapValues { (_, episodes) ->
                    episodes
                        .groupBy { it.uri }
                        .values
                        .map { group ->
                            val first = group.minBy { it.episodeNumber }
                            first.copy(
                                episodeEndNumber =
                                    group
                                        .maxOf { it.episodeNumber }
                                        .takeIf { it > first.episodeNumber }
                            )
                        }
                },
            )
        }

        results.addAll(mergedCandidates)
        return results
    }

    private fun collectShowEpisodes(
        dir: ScanNode,
        identity: String,
        embeddedSeason: Int?,
        seasonsMap: MutableMap<Int, MutableList<ParityEpisode>>,
        depth: Int,
    ) {
        val dirEntries = dir.listFiles()
        val regularBefore = countRegularEpisodes(seasonsMap)
        var foundRegularSeasonSubfolder = false

        for (subEntry in dirEntries) {
            if (subEntry.isDirectory && isSeasonFolder(subEntry.name)) {
                val seasonNum = extractSeasonNumber(subEntry.name)
                if (seasonNum != 0) foundRegularSeasonSubfolder = true
                val eps = mutableListOf<ParityEpisode>()

                val dirFiles = subEntry.listFiles()
                val dirSubtitles = dirFiles.filter { it.isFile && isSubtitleFile(it.name) }

                for (video in dirFiles.filter { it.isFile && isVideoFile(it.name) }) {
                    val nameWithoutExt = video.name.substringBeforeLast('.')
                    val subtitleUri =
                        dirSubtitles
                            .find { it.name.substringBeforeLast('.') == nameWithoutExt }
                            ?.uri
                    val (_, epNums) = parser.parseSeasonAndEpisodeNumbers(nameWithoutExt)
                    for (epNum in epNums) {
                        eps.add(ParityEpisode(video.uri, epNum, subtitleUri))
                    }
                }
                if (eps.isNotEmpty()) {
                    seasonsMap.getOrPut(seasonNum) { mutableListOf() }.addAll(eps)
                }
            }
        }

        if (!foundRegularSeasonSubfolder) {
            val defaultSeasonNum = embeddedSeason ?: 1
            val dirSubtitles = dirEntries.filter { it.isFile && isSubtitleFile(it.name) }

            for (video in dirEntries.filter { it.isFile && isVideoFile(it.name) }) {
                val nameWithoutExt = video.name.substringBeforeLast('.')
                val subtitleUri =
                    dirSubtitles.find { it.name.substringBeforeLast('.') == nameWithoutExt }?.uri
                val (parsedSeason, epNums) = parser.parseSeasonAndEpisodeNumbers(nameWithoutExt)
                val finalSeason = parsedSeason ?: defaultSeasonNum

                val eps = epNums.map { ParityEpisode(video.uri, it, subtitleUri) }
                if (eps.isNotEmpty()) {
                    seasonsMap.getOrPut(finalSeason) { mutableListOf() }.addAll(eps)
                }
            }
        }

        for (subEntry in dirEntries) {
            if (subEntry.isDirectory && isExtrasFolder(subEntry.name)) {
                val dirFiles = subEntry.listFiles()
                val dirSubtitles = dirFiles.filter { it.isFile && isSubtitleFile(it.name) }
                for (video in dirFiles.filter { it.isFile && isVideoFile(it.name) }) {
                    val nameWithoutExt = video.name.substringBeforeLast('.')
                    val subtitleUri =
                        dirSubtitles
                            .find { it.name.substringBeforeLast('.') == nameWithoutExt }
                            ?.uri
                    seasonsMap
                        .getOrPut(0) { mutableListOf() }
                        .add(
                            ParityEpisode(
                                video.uri,
                                0,
                                subtitleUri,
                                extraTitle = parser.parseExtraTitle(nameWithoutExt, identity),
                            )
                        )
                }
            }
        }

        if (
            !foundRegularSeasonSubfolder &&
                countRegularEpisodes(seasonsMap) == regularBefore &&
                depth < Constants.MAX_SHOW_WRAPPER_DEPTH
        ) {
            for (subEntry in dirEntries) {
                if (
                    subEntry.isDirectory &&
                        !isSeasonFolder(subEntry.name) &&
                        !isExtrasFolder(subEntry.name)
                ) {
                    val innerSeason = parser.parseShowFolderIdentity(subEntry.name).first
                    collectShowEpisodes(
                        subEntry,
                        identity,
                        embeddedSeason ?: innerSeason,
                        seasonsMap,
                        depth + 1,
                    )
                }
            }
        }
    }

    private fun countRegularEpisodes(seasonsMap: Map<Int, List<ParityEpisode>>): Int =
        seasonsMap.values.sumOf { eps -> eps.count { it.extraTitle == null } }

    private fun numberExtras(specials: MutableList<ParityEpisode>) {
        val extras = specials.filter { it.extraTitle != null }
        if (extras.isEmpty()) return
        val regular = specials.filter { it.extraTitle == null }
        var next = (regular.maxOfOrNull { it.episodeNumber } ?: 0) + 1
        val numbered =
            extras
                .sortedWith(compareBy<ParityEpisode>({ it.extraTitle!!.lowercase() }, { it.uri }))
                .map { it.copy(episodeNumber = next++) }
        specials.clear()
        specials.addAll(regular)
        specials.addAll(numbered)
    }

    private fun isExtrasFolder(name: String?): Boolean {
        if (name == null) return false
        return name.trim().lowercase() in extrasFolderNames
    }

    private fun isVideoFile(name: String?): Boolean {
        if (name == null) return false
        val extension = name.substringAfterLast('.', "").lowercase()
        return extension in Constants.SUPPORTED_VIDEO_EXTENSIONS
    }

    private fun isSubtitleFile(name: String?): Boolean {
        if (name == null) return false
        val extension = name.substringAfterLast('.', "").lowercase()
        return extension in Constants.SUBTITLE_EXTENSIONS
    }

    private fun isSeasonFolder(name: String?): Boolean {
        if (name == null) return false
        return seasonPattern1.matches(name) ||
            seasonPattern2.matches(name) ||
            specialsPattern.matches(name)
    }

    private fun extractSeasonNumber(name: String): Int {
        if (specialsPattern.matches(name)) return 0
        seasonPattern1.find(name)?.let {
            return it.groupValues[1].toInt()
        }
        seasonPattern2.find(name)?.let {
            return it.groupValues[1].toInt()
        }
        return 1
    }
}
