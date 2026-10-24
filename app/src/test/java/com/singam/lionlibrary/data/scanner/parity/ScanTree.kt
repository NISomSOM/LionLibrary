package com.singam.lionlibrary.data.scanner.parity

import com.singam.lionlibrary.domain.model.MediaType
import java.io.File
import java.net.URLEncoder

/**
 * In-memory stand-in for a SAF folder tree. Children keep the order they were added, which mirrors
 * the provider's listing order; both scanners under comparison see exactly the same order.
 */
class ScanNode(
    val name: String,
    val isDirectory: Boolean,
    val uri: String,
    val documentKey: String,
    val children: MutableList<ScanNode> = mutableListOf(),
) {
    val isFile: Boolean
        get() = !isDirectory

    fun listFiles(): List<ScanNode> = children.toList()
}

/** Builds URIs shaped like ExternalStorageProvider's tree document URIs. */
class ScanTreeBuilder(private val treeDocumentId: String) {
    private val authority = "com.android.externalstorage.documents"
    private val treeUri = "content://$authority/tree/${encode(treeDocumentId)}"

    val root = node("", directory = true, path = "")

    fun dir(path: String): ScanNode = ensure(path, directory = true)

    fun file(path: String): ScanNode = ensure(path, directory = false)

    private fun ensure(path: String, directory: Boolean): ScanNode {
        var current = root
        val parts = path.split('/').filter { it.isNotEmpty() }
        parts.forEachIndexed { index, part ->
            val isLast = index == parts.lastIndex
            val childPath = parts.take(index + 1).joinToString("/")
            current =
                current.children.firstOrNull { it.name == part }
                    ?: node(part, directory = !isLast || directory, path = childPath).also {
                        current.children.add(it)
                    }
        }
        return current
    }

    private fun node(name: String, directory: Boolean, path: String): ScanNode {
        val documentId = if (path.isEmpty()) treeDocumentId else "$treeDocumentId/$path"
        return ScanNode(
            name = name,
            isDirectory = directory,
            uri = "$treeUri/document/${encode(documentId)}",
            documentKey = "$authority:$documentId",
        )
    }

    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    companion object {
        /**
         * Reads the fenced ```tree id=<id> root=<tree document id>``` blocks from
         * References/LIBRARY_FOLDER_STRUCTURES.md. One relative path per line, in device listing
         * order; a trailing `/` marks a folder.
         */
        fun fromReference(reference: File): Map<String, ScanTreeBuilder> {
            val trees = mutableMapOf<String, ScanTreeBuilder>()
            var current: ScanTreeBuilder? = null
            val header = Regex("""^```tree id=(\S+) root=(\S+)\s*$""")
            for (line in reference.readLines(Charsets.UTF_8)) {
                val match = header.find(line)
                when {
                    match != null ->
                        current =
                            ScanTreeBuilder(match.groupValues[2]).also {
                                trees[match.groupValues[1]] = it
                            }
                    line.startsWith("```") -> current = null
                    current != null && line.isNotBlank() ->
                        if (line.endsWith("/")) current.dir(line.dropLast(1))
                        else current.file(line)
                }
            }
            return trees
        }
    }
}

/** Mirrors MediaCandidate with String URIs so it can be compared on the JVM. */
sealed interface ParityCandidate {
    data class Movie(
        val sourceUri: String,
        val title: String,
        val year: Int?,
        val subtitleUri: String?,
    ) : ParityCandidate

    data class Show(val title: String, val seasons: Map<Int, List<ParityEpisode>>) : ParityCandidate

    data class Unknown(
        val sourceUri: String,
        val rawName: String,
        val reason: String,
        val expectedType: MediaType,
    ) : ParityCandidate
}

data class ParityEpisode(
    val uri: String,
    val episodeNumber: Int,
    val subtitleUri: String? = null,
    val episodeEndNumber: Int? = null,
    val extraTitle: String? = null,
)

/** Order-preserving text form: one line per candidate and episode, so a diff names the change. */
fun List<ParityCandidate>.render(): String = buildString {
    for (candidate in this@render) {
        when (candidate) {
            is ParityCandidate.Movie ->
                appendLine(
                    "MOVIE ${candidate.title} | ${candidate.year} | ${candidate.sourceUri} | sub=${candidate.subtitleUri}"
                )
            is ParityCandidate.Unknown ->
                appendLine(
                    "UNKNOWN ${candidate.expectedType} | ${candidate.rawName} | ${candidate.reason} | ${candidate.sourceUri}"
                )
            is ParityCandidate.Show -> {
                appendLine("SHOW ${candidate.title}")
                for ((season, episodes) in candidate.seasons) {
                    appendLine("  SEASON $season")
                    for (ep in episodes) {
                        appendLine(
                            "    E${ep.episodeNumber}-${ep.episodeEndNumber} extra=${ep.extraTitle} | ${ep.uri} | sub=${ep.subtitleUri}"
                        )
                    }
                }
            }
        }
    }
}
