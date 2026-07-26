package com.singam.lionlibrary.data.scanner

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.singam.lionlibrary.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FolderPermissionException(message: String) : Exception(message)

class FolderScanner(
    private val context: Context,
    private val parser: FileNameParser
) {

    private val seasonPattern1 = Regex("""(?:^|\s)Season\s*(\d+)""", RegexOption.IGNORE_CASE)
    private val seasonPattern2 = Regex("""(?:^|\s)S(\d+)(?:\s|$)""", RegexOption.IGNORE_CASE)
    private val specialsPattern = Regex("""^Specials$""", RegexOption.IGNORE_CASE)

    suspend fun scanMoviesFolder(treeUri: Uri): List<MediaCandidate> =
        withContext(Dispatchers.IO) {
            try {
                val root = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext emptyList()
                val results = mutableListOf<MediaCandidate>()

                val allFiles = root.listFiles()
                val subtitles = allFiles.filter { it.isFile && isSubtitleFile(it.name) }

                for (entry in allFiles) {
                    if (entry.isFile && isVideoFile(entry.name)) {
                        val nameWithoutExt = entry.name!!.substringBeforeLast('.')
                        val subtitleUri = subtitles.find { it.name?.substringBeforeLast('.') == nameWithoutExt }?.uri
                        val (title, year) = parser.parseMovieTitle(nameWithoutExt)
                        if (title.isNotBlank()) {
                            results.add(MediaCandidate.Movie(entry.uri, title, year, subtitleUri))
                        } else {
                            results.add(MediaCandidate.Unknown(entry.uri, entry.name ?: "", "Blank title", com.singam.lionlibrary.domain.model.MediaType.MOVIE))
                        }
                    } else if (entry.isDirectory) {
                        val dirFiles = entry.listFiles()
                        val videos = dirFiles.filter { it.isFile && isVideoFile(it.name) }
                        val dirSubtitles = dirFiles.filter { it.isFile && isSubtitleFile(it.name) }
                        if (videos.size == 1) {
                            val video = videos.first()
                            val nameWithoutExt = video.name!!.substringBeforeLast('.')
                            val subtitleUri = dirSubtitles.find { it.name?.substringBeforeLast('.') == nameWithoutExt }?.uri
                            var (title, year) = parser.parseMovieTitle(entry.name ?: "")
                            if (title.isBlank()) {
                                val fallback = parser.parseMovieTitle(nameWithoutExt)
                                title = fallback.first
                                year = fallback.second
                            }
                            if (title.isNotBlank()) {
                                results.add(MediaCandidate.Movie(video.uri, title, year, subtitleUri))
                            } else {
                                results.add(MediaCandidate.Unknown(video.uri, video.name ?: "", "Blank title", com.singam.lionlibrary.domain.model.MediaType.MOVIE))
                            }
                        } else if (videos.size > 1) {
                            for (video in videos) {
                                val nameWithoutExt = video.name!!.substringBeforeLast('.')
                                val subtitleUri = dirSubtitles.find { it.name?.substringBeforeLast('.') == nameWithoutExt }?.uri
                                val (title, year) = parser.parseMovieTitle(nameWithoutExt)
                                if (title.isNotBlank()) {
                                    results.add(MediaCandidate.Movie(video.uri, title, year, subtitleUri))
                                } else {
                                    results.add(MediaCandidate.Unknown(video.uri, video.name ?: "", "Blank title", com.singam.lionlibrary.domain.model.MediaType.MOVIE))
                                }
                            }
                        }
                    }
                }
                results
            } catch (e: SecurityException) {
                throw FolderPermissionException("Permission revoked for folder: $treeUri")
            }
        }

    suspend fun scanShowsFolder(treeUri: Uri): List<MediaCandidate> =
        withContext(Dispatchers.IO) {
            try {
                val root = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext emptyList()
                val rawCandidates = mutableListOf<MediaCandidate.Show>()
                val results = mutableListOf<MediaCandidate>()

                for (entry in root.listFiles()) {
                    if (entry.isDirectory) {
                        val (embeddedSeason, identity) = parser.parseShowFolderIdentity(entry.name ?: "")
                        if (identity.isBlank()) continue

                        val seasonsMap = mutableMapOf<Int, MutableList<EpisodeFile>>()
                        var foundSeasonSubfolder = false

                        for (subEntry in entry.listFiles()) {
                            if (subEntry.isDirectory && isSeasonFolder(subEntry.name)) {
                                foundSeasonSubfolder = true
                                val seasonNum = extractSeasonNumber(subEntry.name!!)
                                val eps = mutableListOf<EpisodeFile>()
                                
                                val dirFiles = subEntry.listFiles()
                                val dirSubtitles = dirFiles.filter { it.isFile && isSubtitleFile(it.name) }
                                
                                for (video in dirFiles.filter { it.isFile && isVideoFile(it.name) }) {
                                    val nameWithoutExt = video.name!!.substringBeforeLast('.')
                                    val subtitleUri = dirSubtitles.find { it.name?.substringBeforeLast('.') == nameWithoutExt }?.uri
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

                        if (!foundSeasonSubfolder) {
                            val defaultSeasonNum = embeddedSeason ?: 1
                            val dirFiles = entry.listFiles()
                            val dirSubtitles = dirFiles.filter { it.isFile && isSubtitleFile(it.name) }
                            
                            for (video in dirFiles.filter { it.isFile && isVideoFile(it.name) }) {
                                val nameWithoutExt = video.name!!.substringBeforeLast('.')
                                val subtitleUri = dirSubtitles.find { it.name?.substringBeforeLast('.') == nameWithoutExt }?.uri
                                val (parsedSeason, epNums) = parser.parseSeasonAndEpisodeNumbers(nameWithoutExt)
                                val finalSeason = parsedSeason ?: defaultSeasonNum
                                
                                val eps = epNums.map { EpisodeFile(video.uri, it, subtitleUri) }
                                if (eps.isNotEmpty()) {
                                    seasonsMap.getOrPut(finalSeason) { mutableListOf() }.addAll(eps)
                                }
                            }
                        }

                        if (seasonsMap.isNotEmpty()) {
                            rawCandidates.add(MediaCandidate.Show(identity, seasonsMap))
                        }
                    } else if (entry.isFile && isVideoFile(entry.name)) {
                        results.add(MediaCandidate.Unknown(
                            entry.uri, 
                            entry.name ?: "", 
                            "Place inside a folder named after the show", 
                            com.singam.lionlibrary.domain.model.MediaType.TV_SHOW
                        ))
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
                    MediaCandidate.Show(identity, mergedSeasons)
                }

                results.addAll(mergedCandidates)
                results
            } catch (e: SecurityException) {
                throw FolderPermissionException("Permission revoked for folder: $treeUri")
            }
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
        return seasonPattern1.matches(name) || seasonPattern2.matches(name) || specialsPattern.matches(name)
    }

    private fun extractSeasonNumber(name: String): Int {
        if (specialsPattern.matches(name)) return 0
        seasonPattern1.find(name)?.let { return it.groupValues[1].toInt() }
        seasonPattern2.find(name)?.let { return it.groupValues[1].toInt() }
        return 1
    }
}
