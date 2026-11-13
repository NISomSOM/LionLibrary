package com.singam.lionlibrary.data.scanner

import com.singam.lionlibrary.domain.model.MediaType
import com.singam.lionlibrary.util.Constants

/**
 * One child of a listed folder. [U] is the document address type: `Uri` in the app, `String` in JVM
 * tests. The flags follow DocumentFile: a directory has the directory MIME type, a file has any
 * other non-empty type, and a row can be neither. [documentKey] is `authority:documentId`.
 */
class FolderEntry<U>(
    val name: String?,
    val isDirectory: Boolean,
    val isFile: Boolean,
    val uri: U,
    val documentKey: String?,
)

/**
 * Lists a folder's children in provider order, one request per folder. Implementations must throw
 * when the listing fails or is incomplete; an empty list means the folder is really empty.
 */
fun interface FolderLister<U> {
    fun children(parent: FolderEntry<U>): List<FolderEntry<U>>
}

/** Classifier output; [FolderScanner] converts it to [MediaCandidate]. */
sealed interface ScannedCandidate<out U> {
    data class Movie<U>(val sourceUri: U, val title: String, val year: Int?, val subtitleUri: U?) :
        ScannedCandidate<U>

    data class Show<U>(val title: String, val seasons: Map<Int, List<ScannedEpisode<U>>>) :
        ScannedCandidate<U>

    data class Unknown<U>(
        val sourceUri: U,
        val rawName: String,
        val reason: String,
        val expectedType: MediaType,
    ) : ScannedCandidate<U>
}

data class ScannedEpisode<U>(
    val uri: U,
    val episodeNumber: Int,
    val subtitleUri: U? = null,
    val episodeEndNumber: Int? = null,
    val extraTitle: String? = null,
)

/**
 * Movie and show folder rules, free of Android types so ScannerParityTest can run this exact code
 * against the frozen pre-rewrite scanner. Candidate order, addresses and subtitle pairing must stay
 * identical to that reference; see AGENT.md section 8 before changing a rule.
 */
class FolderClassifier<U>(private val parser: FileNameParser, private val lister: FolderLister<U>) {

    private val seasonPattern1 = Regex("""(?:^|\s)Season\s*(\d+)""", RegexOption.IGNORE_CASE)
    private val seasonPattern2 = Regex("""(?:^|\s)S(\d+)(?:\s|$)""", RegexOption.IGNORE_CASE)
    private val specialsPattern = Regex("""^Specials?$""", RegexOption.IGNORE_CASE)

    // Non-season show subfolders whose videos are kept as extras (Season 0).
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

    /** A listed folder: its videos in order plus the first subtitle for each basename. */
    private inner class Listing(val entries: List<FolderEntry<U>>) {
        val videos: List<FolderEntry<U>> = entries.filter { it.isFile && isVideoFile(it.name) }
        private val subtitles: Map<String, U> = run {
            val firstByBasename = HashMap<String, U>()
            for (entry in entries) {
                if (entry.isFile && isSubtitleFile(entry.name)) {
                    val base = entry.name!!.substringBeforeLast('.')
                    if (base !in firstByBasename) firstByBasename[base] = entry.uri
                }
            }
            firstByBasename
        }

        fun subtitleFor(nameWithoutExt: String): U? = subtitles[nameWithoutExt]
    }

    private fun list(dir: FolderEntry<U>) = Listing(lister.children(dir))

    // excludeKey: the Shows folder, skipped if it lives inside the Movies folder.
    fun scanMovies(root: FolderEntry<U>, excludeKey: String? = null): List<ScannedCandidate<U>> {
        val results = mutableListOf<ScannedCandidate<U>>()
        val listing = list(root)
        for (entry in listing.entries) {
            if (entry.isFile && isVideoFile(entry.name)) {
                results.add(movieFromFileName(entry, listing))
            } else if (entry.isDirectory) {
                scanMovieDirectory(entry, 1, excludeKey, results)
            }
        }
        return results
    }

    private fun movieFromFileName(video: FolderEntry<U>, listing: Listing): ScannedCandidate<U> {
        val nameWithoutExt = video.name!!.substringBeforeLast('.')
        val (title, year) = parser.parseMovieTitle(nameWithoutExt)
        return movieOrUnknown(video, title, year, listing.subtitleFor(nameWithoutExt))
    }

    private fun movieOrUnknown(
        video: FolderEntry<U>,
        title: String,
        year: Int?,
        subtitleUri: U?,
    ): ScannedCandidate<U> =
        if (title.isNotBlank()) ScannedCandidate.Movie(video.uri, title, year, subtitleUri)
        else ScannedCandidate.Unknown(video.uri, video.name ?: "", "Blank title", MediaType.MOVIE)

    private fun scanMovieDirectory(
        dir: FolderEntry<U>,
        depth: Int,
        excludeKey: String?,
        results: MutableList<ScannedCandidate<U>>,
    ) {
        if (excludeKey != null && dir.documentKey == excludeKey) return

        val listing = list(dir)
        val videos = listing.videos
        if (videos.size == 1) {
            val video = videos.first()
            val nameWithoutExt = video.name!!.substringBeforeLast('.')
            var (title, year) = parser.parseMovieTitle(dir.name ?: "")
            if (title.isBlank()) {
                val fallback = parser.parseMovieTitle(nameWithoutExt)
                title = fallback.first
                year = fallback.second
            }
            results.add(movieOrUnknown(video, title, year, listing.subtitleFor(nameWithoutExt)))
        } else if (videos.size > 1) {
            for (video in videos) results.add(movieFromFileName(video, listing))
        } else if (depth < Constants.MAX_MOVIE_FOLDER_DEPTH) {
            // No videos here: treat as a collection folder (e.g. "Ghibli Films") and descend.
            for (sub in listing.entries) {
                if (sub.isDirectory) scanMovieDirectory(sub, depth + 1, excludeKey, results)
            }
        }
    }

    fun scanShows(root: FolderEntry<U>): List<ScannedCandidate<U>> {
        val rawCandidates = mutableListOf<ScannedCandidate.Show<U>>()
        val results = mutableListOf<ScannedCandidate<U>>()

        for (entry in lister.children(root)) {
            if (entry.isDirectory) {
                val (embeddedSeason, identity) = parser.parseShowFolderIdentity(entry.name ?: "")
                if (identity.isBlank()) continue

                val seasonsMap = mutableMapOf<Int, MutableList<ScannedEpisode<U>>>()
                collectShowEpisodes(entry, identity, embeddedSeason, seasonsMap, 1)

                if (seasonsMap.isNotEmpty()) {
                    rawCandidates.add(ScannedCandidate.Show(identity, seasonsMap))
                }
            } else if (entry.isFile && isVideoFile(entry.name)) {
                results.add(
                    ScannedCandidate.Unknown(
                        entry.uri,
                        entry.name ?: "",
                        "Place inside a folder named after the show",
                        MediaType.TV_SHOW,
                    )
                )
            }
        }

        // Merge identical show results.
        val grouped = rawCandidates.groupBy { it.title }
        val mergedCandidates = grouped.map { (identity, candidates) ->
            val mergedSeasons = mutableMapOf<Int, MutableList<ScannedEpisode<U>>>()
            for (candidate in candidates) {
                for ((season, eps) in candidate.seasons) {
                    mergedSeasons.getOrPut(season) { mutableListOf() }.addAll(eps)
                }
            }
            mergedSeasons[0]?.let { numberExtras(it) }
            ScannedCandidate.Show(
                identity,
                mergedSeasons.mapValues { (_, episodes) ->
                    episodes
                        .groupBy { it.uri.toString() }
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
        dir: FolderEntry<U>,
        identity: String,
        embeddedSeason: Int?,
        seasonsMap: MutableMap<Int, MutableList<ScannedEpisode<U>>>,
        depth: Int,
    ) {
        val listing = list(dir)
        val regularBefore = countRegularEpisodes(seasonsMap)
        var foundRegularSeasonSubfolder = false

        for (subEntry in listing.entries) {
            if (subEntry.isDirectory && isSeasonFolder(subEntry.name)) {
                val seasonNum = extractSeasonNumber(subEntry.name!!)
                if (seasonNum != 0) foundRegularSeasonSubfolder = true
                val eps = mutableListOf<ScannedEpisode<U>>()

                val seasonListing = list(subEntry)
                for (video in seasonListing.videos) {
                    val nameWithoutExt = video.name!!.substringBeforeLast('.')
                    val subtitleUri = seasonListing.subtitleFor(nameWithoutExt)
                    val (_, epNums) = parser.parseSeasonAndEpisodeNumbers(nameWithoutExt)
                    for (epNum in epNums) {
                        eps.add(ScannedEpisode(video.uri, epNum, subtitleUri))
                    }
                }
                if (eps.isNotEmpty()) {
                    seasonsMap.getOrPut(seasonNum) { mutableListOf() }.addAll(eps)
                }
            }
        }

        // Loose videos count unless a regular season folder exists (a Specials folder alone doesn't
        // hide them).
        if (!foundRegularSeasonSubfolder) {
            val defaultSeasonNum = embeddedSeason ?: 1

            for (video in listing.videos) {
                val nameWithoutExt = video.name!!.substringBeforeLast('.')
                val subtitleUri = listing.subtitleFor(nameWithoutExt)
                val (parsedSeason, epNums) = parser.parseSeasonAndEpisodeNumbers(nameWithoutExt)
                val finalSeason = parsedSeason ?: defaultSeasonNum

                val eps = epNums.map { ScannedEpisode(video.uri, it, subtitleUri) }
                if (eps.isNotEmpty()) {
                    seasonsMap.getOrPut(finalSeason) { mutableListOf() }.addAll(eps)
                }
            }
        }

        // Extras folders (Trailers, Other, NCOP...) go to Season 0; numbered in numberExtras().
        for (subEntry in listing.entries) {
            if (subEntry.isDirectory && isExtrasFolder(subEntry.name)) {
                val extrasListing = list(subEntry)
                for (video in extrasListing.videos) {
                    val nameWithoutExt = video.name!!.substringBeforeLast('.')
                    seasonsMap
                        .getOrPut(0) { mutableListOf() }
                        .add(
                            ScannedEpisode(
                                video.uri,
                                0,
                                extrasListing.subtitleFor(nameWithoutExt),
                                extraTitle = parser.parseExtraTitle(nameWithoutExt, identity),
                            )
                        )
                }
            }
        }

        // Nothing found: the episodes may sit in a wrapper subfolder (Show/Show/S01E01.mkv).
        // Title stays from the outer show folder.
        if (
            !foundRegularSeasonSubfolder &&
                countRegularEpisodes(seasonsMap) == regularBefore &&
                depth < Constants.MAX_SHOW_WRAPPER_DEPTH
        ) {
            for (subEntry in listing.entries) {
                if (
                    subEntry.isDirectory &&
                        !isSeasonFolder(subEntry.name) &&
                        !isExtrasFolder(subEntry.name)
                ) {
                    val innerSeason = parser.parseShowFolderIdentity(subEntry.name ?: "").first
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

    private fun countRegularEpisodes(seasonsMap: Map<Int, List<ScannedEpisode<U>>>): Int =
        seasonsMap.values.sumOf { eps -> eps.count { it.extraTitle == null } }

    // Number extras after any real specials, in a stable order.
    private fun numberExtras(specials: MutableList<ScannedEpisode<U>>) {
        val extras = specials.filter { it.extraTitle != null }
        if (extras.isEmpty()) return
        val regular = specials.filter { it.extraTitle == null }
        var next = (regular.maxOfOrNull { it.episodeNumber } ?: 0) + 1
        val numbered =
            extras
                .sortedWith(
                    compareBy<ScannedEpisode<U>>(
                        { it.extraTitle!!.lowercase() },
                        { it.uri.toString() },
                    )
                )
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
