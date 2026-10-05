package dev.alllexey.itmowidgets.core.storage

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import kotlinx.coroutines.delay
import okio.FileSystem
import okio.IOException
import okio.Path
import platform.posix.EINTR
import platform.posix.EWOULDBLOCK
import platform.posix.LOCK_EX
import platform.posix.LOCK_NB
import platform.posix.LOCK_UN
import platform.posix.O_CLOEXEC
import platform.posix.O_CREAT
import platform.posix.O_RDWR
import platform.posix.close
import platform.posix.errno
import platform.posix.flock
import platform.posix.open
import platform.posix.strerror

/**
 * The iOS [CrossProcessLock]: `flock(2)` on `<locks>/<name>.lock` in the App Group container, so the app and the
 * notification service never run the same guarded work (one token refresh) at once. A holder in this process
 * first takes a per-name in-process lock, then polls the file lock without blocking a thread, so waiting suspends.
 *
 * Trap (0xdead10cc): iOS kills a suspended process that holds a lock on a file in a shared container. Hold the lock
 * only around the guarded call, never across work that can outlive the app's time in the foreground.
 */
class FileCrossProcessLock(
    private val locks: Path,
    private val fileSystem: FileSystem = SystemFileSystem
) : CrossProcessLock {

    private val inProcess = SingleProcessLock()

    override suspend fun <T> withLock(name: String, action: suspend () -> T): T {
        require(name.isNotEmpty() && NAME.matches(name)) { "'$name' is not a lock name" }
        return inProcess.withLock(name) {
            val descriptor = acquire(locks / "$name.lock")
            try {
                action()
            } finally {
                release(descriptor)
            }
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private suspend fun acquire(file: Path): Int {
        fileSystem.createDirectories(locks)
        val descriptor = open(file.toString(), O_RDWR or O_CREAT or O_CLOEXEC, LOCK_FILE_MODE)
        if (descriptor < 0) throw IOException("Cannot open lock ${file.name}: ${lastError()}")
        try {
            var wait = FIRST_WAIT
            while (flock(descriptor, LOCK_EX or LOCK_NB) != 0) {
                val error = errno
                if (error != EWOULDBLOCK && error != EINTR) {
                    throw IOException("Cannot lock ${file.name}: ${lastError()}")
                }
                delay(wait)
                wait = (wait * 2).coerceAtMost(LONGEST_WAIT)
            }
            return descriptor
        } catch (failure: Throwable) {
            close(descriptor)
            throw failure
        }
    }

    private fun release(descriptor: Int) {
        flock(descriptor, LOCK_UN)
        close(descriptor)
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun lastError(): String = strerror(errno)?.toKString() ?: "errno $errno"

    private companion object {
        val NAME = Regex("[A-Za-z0-9._-]+")
        val FIRST_WAIT: Duration = 5.milliseconds
        val LONGEST_WAIT: Duration = 100.milliseconds

        /** rw------- */
        const val LOCK_FILE_MODE = 0x180
    }
}
