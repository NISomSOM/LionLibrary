package com.singam.lionlibrary.domain.repository

/** Persisted folder (SAF tree) grants held by the app. */
interface FolderAccessRepository {
    /**
     * Releases the persisted grant for [previousUri] after a root was replaced, unless one of
     * [inUse] (the new root, the other root) is the same tree or lies inside it. Never touches
     * files; failures leave the grant in place.
     */
    suspend fun releaseIfUnused(previousUri: String, inUse: List<String>)
}
