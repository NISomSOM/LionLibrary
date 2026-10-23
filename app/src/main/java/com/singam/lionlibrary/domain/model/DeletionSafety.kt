package com.singam.lionlibrary.domain.model

data class StorageNode(
    val id: String,
    val parentId: String?,
    val name: String,
    val directory: Boolean,
    val deletable: Boolean,
    val size: Long,
    val modified: Long,
    val video: Boolean,
)

/** Document IDs are opaque: ancestry comes only from a verified provider listing. */
data class StorageTree(
    val authority: String,
    val rootId: String,
    val nodes: Map<String, StorageNode>,
) {
    fun subtree(id: String): Set<String> {
        val result = mutableSetOf<String>()
        val children = nodes.values.groupBy { it.parentId }
        val pending = ArrayDeque<String>().apply { add(id) }
        while (pending.isNotEmpty()) {
            val next = pending.removeFirst()
            check(result.add(next)) { "Cyclic document tree" }
            children[next].orEmpty().forEach { pending.add(it.id) }
        }
        return result
    }
}

object DeletionSafety {
    /** The same direct child of Shows that determines scanner show identity. Never the root. */
    fun showFolder(
        tree: StorageTree,
        episodes: Set<String>,
        otherFiles: Set<String>,
    ): StorageNode? {
        if (episodes.isEmpty()) return null
        val folders =
            episodes
                .map { episode ->
                    var node = tree.nodes[episode] ?: return null
                    val visited = mutableSetOf<String>()
                    while (node.parentId != tree.rootId) {
                        if (!visited.add(node.id)) return null
                        node = tree.nodes[node.parentId] ?: return null
                    }
                    node.id
                }
                .toSet()
        if (folders.size != 1) return null
        val folder = tree.nodes[folders.single()] ?: return null
        if (!folder.directory || !folder.deletable || folder.id == tree.rootId) return null
        val contents = tree.subtree(folder.id)
        if (otherFiles.any { it in contents }) return null
        // An unindexed video may belong to somebody else. Require a scan before deleting it.
        if (contents.any { tree.nodes[it]?.video == true && it !in episodes }) return null
        return folder
    }
}
