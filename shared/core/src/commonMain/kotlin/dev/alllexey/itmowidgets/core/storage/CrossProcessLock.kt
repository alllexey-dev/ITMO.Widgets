package dev.alllexey.itmowidgets.core.storage

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Mutual exclusion by name between every process that touches the same state: the token refresh of the app and of
 * an iOS extension, for one. Not reentrant: a nested [withLock] of the same name waits for itself forever.
 *
 * Hold it only around the guarded work: iOS kills a suspended process that still holds a lock on a shared-container
 * file (0xdead10cc).
 */
interface CrossProcessLock {

    /** Runs [action] while no other holder of [name], in this or another process, runs; returns its result. */
    suspend fun <T> withLock(name: String, action: suspend () -> T): T
}

/**
 * A [CrossProcessLock] for a platform whose readers and writers of the guarded state share one process: Android,
 * where the app, its widgets and its workers run in the same process. One [Mutex] per name. iOS extensions are
 * processes of their own and need the file lock instead.
 */
class SingleProcessLock : CrossProcessLock {

    private val guard = SynchronizedObject()
    private val mutexes = HashMap<String, Mutex>()

    override suspend fun <T> withLock(name: String, action: suspend () -> T): T = mutex(name).withLock { action() }

    private fun mutex(name: String): Mutex = synchronized(guard) { mutexes.getOrPut(name) { Mutex() } }
}
