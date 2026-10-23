package com.singam.lionlibrary.data.storage

import kotlinx.coroutines.sync.Mutex

/** Scanner writes, inventory cleanup and explicit file deletion share one owner. */
class LibraryMutationGate {
    val mutex = Mutex()
}
