package com.singam.lionlibrary.domain.model

/** A SAF tree identity: provider authority plus its tree document id (opaque to the app). */
data class FolderTree(val authority: String, val documentId: String)

object FolderGrantPolicy {
    /**
     * Whether the grant for [previous] can be released while [inUse] stay configured. It is kept
     * when any of them is the same tree or nested inside it. Nesting is only recognised for ids
     * that follow the common `volume:path/segments` form; for opaque ids it reports "not nested",
     * which is safe because every configured root holds its own grant from the folder picker.
     */
    fun canRelease(previous: FolderTree, inUse: List<FolderTree?>): Boolean =
        inUse.filterNotNull().none { it == previous || isInside(it, previous) }

    private fun isInside(child: FolderTree, parent: FolderTree): Boolean {
        if (child.authority != parent.authority) return false
        val parentId = parent.documentId
        if (parentId.isEmpty() || child.documentId.length <= parentId.length) return false
        if (!child.documentId.startsWith(parentId)) return false
        // "primary:" is a volume root; "primary:Movies" needs a "/" boundary, so that
        // "primary:Movies2" is not treated as inside "primary:Movies".
        return parentId.endsWith(':') ||
            parentId.endsWith('/') ||
            child.documentId[parentId.length] == '/'
    }
}
