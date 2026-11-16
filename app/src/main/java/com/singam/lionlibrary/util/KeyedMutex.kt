package com.singam.lionlibrary.util

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One mutex per key, removed again once no caller holds or waits for it, so per-title or per-file
 * lock maps do not grow for the lifetime of the app. Callers with the same key are serialized
 * exactly like a `ConcurrentHashMap<K, Mutex>` would serialize them.
 */
class KeyedMutex<K : Any> {
    private class Entry {
        val mutex = Mutex()
        var users = 0
    }

    private val entries = HashMap<K, Entry>()

    /** Number of keys currently held or awaited; zero when idle. */
    val activeKeys: Int
        get() = synchronized(entries) { entries.size }

    suspend fun <T> withLock(key: K, block: suspend () -> T): T {
        val entry = synchronized(entries) { entries.getOrPut(key, ::Entry).also { it.users++ } }
        try {
            return entry.mutex.withLock { block() }
        } finally {
            synchronized(entries) {
                entry.users--
                if (entry.users == 0) entries.remove(key)
            }
        }
    }
}
