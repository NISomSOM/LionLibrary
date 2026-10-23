package com.singam.lionlibrary.data.storage

import com.singam.lionlibrary.domain.model.StorageTree

data class DocumentLocation(val authority: String, val treeId: String, val id: String)

enum class DocumentPresence {
    PRESENT,
    MISSING,
    UNAVAILABLE,
}

/** All failures/partial listings must throw, never masquerade as empty storage. */
interface LibraryDocuments {
    fun location(uri: String): DocumentLocation?

    suspend fun inventory(rootUri: String): StorageTree

    suspend fun presence(rootUri: String, documentId: String): DocumentPresence

    suspend fun delete(rootUri: String, documentId: String): Boolean
}
