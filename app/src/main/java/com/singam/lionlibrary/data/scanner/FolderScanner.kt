package com.singam.lionlibrary.data.scanner

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.singam.lionlibrary.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FolderPermissionException(message: String) : Exception(message)

class FolderScanner(private val context: Context, private val parser: FileNameParser) {

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

    // excludedTreeUri: the Shows folder, skipped if it lives inside the Movies folder.
    suspend fun scanMoviesFolder(treeUri: Uri, excludedTreeUri: Uri? = null): List<MediaCandidate> =
        withContext(Dispatchers.IO) {
            try {
                val root =
                    DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext emptyList()
                val results = mutableListOf<MediaCandidate>()
                val excludeKey = excludedTreeUri?.let { uri ->
                    runCatching {
                        "${uri.authority}:${DocumentsContract.getTreeDocumentId(uri)}"
                    }
                        .getOrNull()
                }

                val allFiles = root.listFiles()
                val subtitles = allFiles.filter { it.isFile && isSubtitleFile(it.name) }

                for (entry in allFiles) {
                    if (entry.isFile && isVideoFile(entry.name)) {
                        val nameWithoutExt = entry.name!!.substringBeforeLast('.')
                        val subtitleUri =
                            subtitles
                                .find { it.name?.substringBeforeLast('.') == nameWithoutExt }
                                ?.uri
                        val (title, year) = parser.parseMovieTitle(nameWithoutExt)
                        if (title.isNotBlank()) {
                            results.add(MediaCandidate.Movie(entry.uri, title, year, subtitleUri))
                        } else {
                            results.add(
                                MediaCandidate.Unknown(
                                    entry.uri,
                                    entry.name ?: "",
                                    "Blank title",
                                    com.singam.lionlibrary.domain.model.MediaType.MOVIE,
                                )
                            )
                        }
                    } else if (entry.isDirectory) {
                        scanMovieDirectory(entry, 1, excludeKey, results)
                    }
                }
                results
            } catch (e: SecurityException) {
                throw FolderPermissionException("Permission revoked for folder: $treeUri")
            }
        }

    private fun scanMovieDirectory(
        dir: DocumentFile,
        depth: Int,
        excludeKey: String?,
        results: MutableList<MediaCandidate>,
    ) {
        if (excludeKey != null && documentKey(dir) == excludeKey) return

        val dirFiles = dir.listFiles()
        val videos = dirFiles.filter { it.isFile && isVideoFile(it.name) }
        val dirSubtitles = dirFiles.filter { it.isFile && isSubtitleFile(it.name) }
        if (videos.size == 1) {
            val video = videos.first()
            val nameWithoutExt = video.name!!.substringBeforeLast('.')
            val subtitleUri =
                dirSubtitles.find { it.name?.substringBeforeLast('.') == nameWithoutExt }?.uri
            var (title, year) = parser.parseMovieTitle(dir.name ?: "")
            if (title.isBlank()) {
                val fallback = parser.parseMovieTitle(nameWithoutExt)
                title = fallback.first
                year = fallback.second
            }
            if (title.isNotBlank()) {
                results.add(MediaCandidate.Movie(video.uri, title, year, subtitleUri))
            } else {
                results.add(
                    MediaCandidate.Unknown(
                        video.uri,
                        video.name ?: "",
                        "Blank title",
                        com.singam.lionlibrary.domain.model.MediaType.MOVIE,
                    )
                )
            }
        } else if (videos.size > 1) {
            for (video in videos) {
                val nameWithoutExt = video.name!!.substringBeforeLast('.')
                val subtitleUri =
                    dirSubtitles.find { it.name?.substringBeforeLast('.') == nameWithoutExt }?.uri
                val (title, year) = parser.parseMovieTitle(nameWithoutExt)
                if (title.isNotBlank()) {
                    results.add(MediaCandidate.Movie(video.uri, title, year, subtitleUri))
                } else {
                    results.add(
                        MediaCandidate.Unknown(
                            video.uri,
                            video.name ?: "",
                            "Blank title",
                            com.singam.lionlibrary.domain.model.MediaType.MOVIE,
                        )
                    )
                }
            }
        } else if (depth < Constants.MAX_MOVIE_FOLDER_DEPTH) {
            // No videos here: treat as a collection folder (e.g. "Ghibli Films") and descend.
            for (sub in dirFiles) {
                if (sub.isDirectory) scanMovieDirectory(sub, depth + 1, excludeKey, results)
            }
        }
    }

    suspend fun scanShowsFolder(treeUri: Uri): List<MediaCandidate> =
        withContext(Dispatchers.IO) {
            try {
                val root =
                    DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext emptyList()
                val rawCandidates = mutableListOf<MediaCandidate.Show>()
                val results = mutableListOf<MediaCandidate>()

                for (entry in root.listFiles()) {
                    if (entry.isDirectory) {
                        val (embeddedSeason, identity) =
                            parser.parseShowFolderIdentity(entry.name ?: "")
                        if (identity.isBlank()) continue

                        val seasonsMap = mutableMapOf<Int, MutableList<EpisodeFile>>()
                        collectShowEpisodes(entry, identity, embeddedSeason, seasonsMap, 1)

                        if (seasonsMap.isNotEmpty()) {
                            rawCandidates.add(MediaCandidate.Show(identity, seasonsMap))
                        }
                    } else if (entry.isFile && isVideoFile(entry.name)) {
                        results.add(
                            MediaCandidate.Unknown(
                                entry.uri,
                                entry.name ?: "",
                                "Place inside a folder named after the show",
                                com.singam.lionlibrary.domain.model.MediaType.TV_SHOW,
                            )
                        )
                    }
                }

                // Merge identical show results.
                val grouped = rawCandidates.groupBy { it.title }
                val mergedCandidates = grouped.map { (identity, candidates) ->
                    val mergedSeasons = mutableMapOf<Int, MutableList<EpisodeFile>>()
                    for (candidate in candidates) {
                        for ((season, eps) in candidate.seasons) {
                            mergedSeasons.getOrPut(season) { mutableListOf() }.addAll(eps)
                        }
                    }
                    mergedSeasons[0]?.let { numberExtras(it) }
                    MediaCandidate.Show(
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
                results
            } catch (e: SecurityException) {
                throw FolderPermissionException("Permission revoked for folder: $treeUri")
            }
        }

    private fun collectShowEpisodes(
        dir: DocumentFile,
        identity: String,
        embeddedSeason: Int?,
        seasonsMap: MutableMap<Int, MutableList<EpisodeFile>>,
        depth: Int,
    ) {
        val dirEntries = dir.listFiles()
        val regularBefore = countRegularEpisodes(seasonsMap)
        var foundRegularSeasonSubfolder = false

        for (subEntry in dirEntries) {
            if (subEntry.isDirectory && isSeasonFolder(subEntry.name)) {
                val seasonNum = extractSeasonNumber(subEntry.name!!)
                if (seasonNum != 0) foundRegularSeasonSubfolder = true
                val eps = mutableListOf<EpisodeFile>()

                val dirFiles = subEntry.listFiles()
                val dirSubtitles = dirFiles.filter { it.isFile && isSubtitleFile(it.name) }

                for (video in dirFiles.filter { it.isFile && isVideoFile(it.name) }) {
                    val nameWithoutExt = video.name!!.substringBeforeLast('.')
                    val subtitleUri =
                        dirSubtitles
                            .find { it.name?.substringBeforeLast('.') == nameWithoutExt }
                            ?.uri
                    val (_, epNums) = parser.parseSeasonAndEpisodeNumbers(nameWithoutExt)
                    for (epNum in epNums) {
                        eps.add(EpisodeFile(video.uri, epNum, subtitleUri))
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
            val dirSubtitles = dirEntries.filter { it.isFile && isSubtitleFile(it.name) }

            for (video in dirEntries.filter { it.isFile && isVideoFile(it.name) }) {
                val nameWithoutExt = video.name!!.substringBeforeLast('.')
                val subtitleUri =
                    dirSubtitles.find { it.name?.substringBeforeLast('.') == nameWithoutExt }?.uri
                val (parsedSeason, epNums) = parser.parseSeasonAndEpisodeNumbers(nameWithoutExt)
                val finalSeason = parsedSeason ?: defaultSeasonNum

                val eps = epNums.map { EpisodeFile(video.uri, it, subtitleUri) }
                if (eps.isNotEmpty()) {
                    seasonsMap.getOrPut(finalSeason) { mutableListOf() }.addAll(eps)
                }
            }
        }

        // Extras folders (Trailers, Other, NCOP...) go to Season 0; numbered in numberExtras().
        for (subEntry in dirEntries) {
            if (subEntry.isDirectory && isExtrasFolder(subEntry.name)) {
                val dirFiles = subEntry.listFiles()
                val dirSubtitles = dirFiles.filter { it.isFile && isSubtitleFile(it.name) }
                for (video in dirFiles.filter { it.isFile && isVideoFile(it.name) }) {
                    val nameWithoutExt = video.name!!.substringBeforeLast('.')
                    val subtitleUri =
                        dirSubtitles
                            .find { it.name?.substringBeforeLast('.') == nameWithoutExt }
                            ?.uri
                    seasonsMap
                        .getOrPut(0) { mutableListOf() }
                        .add(
                            EpisodeFile(
                                video.uri,
                                0,
                                subtitleUri,
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
            for (subEntry in dirEntries) {
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

    private fun countRegularEpisodes(seasonsMap: Map<Int, List<EpisodeFile>>): Int =
        seasonsMap.values.sumOf { eps -> eps.count { it.extraTitle == null } }

    // Number extras after any real specials, in a stable order.
    private fun numberExtras(specials: MutableList<EpisodeFile>) {
        val extras = specials.filter { it.extraTitle != null }
        if (extras.isEmpty()) return
        val regular = specials.filter { it.extraTitle == null }
        var next = (regular.maxOfOrNull { it.episodeNumber } ?: 0) + 1
        val numbered =
            extras
                .sortedWith(
                    compareBy<EpisodeFile>({ it.extraTitle!!.lowercase() }, { it.uri.toString() })
                )
                .map { it.copy(episodeNumber = next++) }
        specials.clear()
        specials.addAll(regular)
        specials.addAll(numbered)
    }

    private fun documentKey(file: DocumentFile): String? = runCatching {
        "${file.uri.authority}:${DocumentsContract.getDocumentId(file.uri)}"
    }
        .getOrNull()

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
