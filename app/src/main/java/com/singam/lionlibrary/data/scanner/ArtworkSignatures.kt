package com.singam.lionlibrary.data.scanner

import java.io.File
import java.io.IOException

/** Size and modification time of an artwork file when it last decoded successfully. */
internal data class ArtworkSignature(val length: Long, val lastModified: Long)

/**
 * Remembers artwork files that already passed a full decode, so a refresh can skip decoding an
 * unchanged file. A file is trusted only while both its size and modification time still match;
 * new, changed or unrecorded files are decoded as before. Kept in memory plus one small private
 * text file (outside the artwork folder, so orphan pruning never sees it). No Room data.
 */
internal class ArtworkSignatures(private val store: File) {
    private val entries = HashMap<String, ArtworkSignature>()
    private var loaded = false
    private var dirty = false

    @Synchronized
    fun matches(path: String, current: ArtworkSignature): Boolean {
        load()
        return entries[path] == current
    }

    @Synchronized
    fun record(path: String, signature: ArtworkSignature) {
        load()
        if (entries.put(path, signature) != signature) dirty = true
    }

    @Synchronized
    fun forget(path: String) {
        load()
        if (entries.remove(path) != null) dirty = true
    }

    /** Writes changes atomically; a failed write only means more decoding next time. */
    @Synchronized
    fun persist() {
        if (!dirty) return
        val staging = File(store.parentFile, "${store.name}.tmp")
        try {
            staging.writeText(encode(entries))
            if (staging.renameTo(store) || (store.delete() && staging.renameTo(store)))
                dirty = false
        } catch (_: IOException) {
            // Keep the in-memory copy; next persist retries.
        } finally {
            staging.delete()
        }
    }

    private fun load() {
        if (loaded) return
        loaded = true
        val text =
            try {
                if (store.isFile) store.readText() else return
            } catch (_: IOException) {
                return
            }
        entries.putAll(decode(text))
    }

    companion object {
        /** One `length<TAB>lastModified<TAB>path` line per file. */
        fun encode(values: Map<String, ArtworkSignature>): String =
            values.entries
                .sortedBy { it.key }
                .joinToString("") { (path, sig) -> "${sig.length}\t${sig.lastModified}\t$path\n" }

        /** Damaged lines are ignored; those files are simply decoded again. */
        fun decode(text: String): Map<String, ArtworkSignature> =
            text
                .lineSequence()
                .mapNotNull { line ->
                    val parts = line.split('\t', limit = 3)
                    if (parts.size != 3 || parts[2].isEmpty()) return@mapNotNull null
                    val length =
                        parts[0].toLongOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
                    val modified = parts[1].toLongOrNull() ?: return@mapNotNull null
                    parts[2] to ArtworkSignature(length, modified)
                }
                .toMap()
    }
}
