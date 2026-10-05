package dev.alllexey.itmowidgets.core.storage

import dev.alllexey.itmowidgets.core.testing.CrossProcessLockContract
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import okio.FileSystem
import platform.posix.EWOULDBLOCK
import platform.posix.LOCK_EX
import platform.posix.LOCK_NB
import platform.posix.LOCK_UN
import platform.posix.O_RDWR
import platform.posix.close
import platform.posix.errno
import platform.posix.flock
import platform.posix.open

class FileCrossProcessLockTest {

    private val temporary = TemporaryDirectory()
    private val locks = temporary.root / "group" / "locks"

    @AfterTest
    fun deleteTemporary() = temporary.delete()

    /** Every opener is a lock of its own over the same directory, as the app and the notification service are. */
    @Test
    fun keepsTheCrossProcessLockContract() = CrossProcessLockContract.checkAll {
        val space = temporary.root / "space-${spaces++}"
        val opener: () -> CrossProcessLock = { FileCrossProcessLock(space) }
        opener
    }

    @Test
    fun holdsAFileLockThatAnotherDescriptorCannotTake() = runTest {
        FileCrossProcessLock(locks).withLock("myitmo-refresh") {
            val file = locks / "myitmo-refresh.lock"
            assertTrue(FileSystem.SYSTEM.exists(file))
            assertEquals(EWOULDBLOCK, tryLockFromAnotherDescriptor(file.toString()))
        }
        assertEquals(0, tryLockFromAnotherDescriptor((locks / "myitmo-refresh.lock").toString()))
    }

    @Test
    fun admitsOneHolderAtATimeUnderContention() = runTest {
        val first = FileCrossProcessLock(locks)
        val second = FileCrossProcessLock(locks)
        var inside = 0
        var mostInside = 0
        var entries = 0

        withContext(Dispatchers.Default) {
            (0 until HOLDERS).map { index ->
                async {
                    (if (index % 2 == 0) first else second).withLock("contended") {
                        inside++
                        mostInside = maxOf(mostInside, inside)
                        yield()
                        entries++
                        inside--
                    }
                }
            }.awaitAll()
        }

        assertEquals(1, mostInside)
        assertEquals(HOLDERS, entries)
    }

    @Test
    fun refusesANameThatIsNotAFileName() = runTest {
        listOf("", "a/b", "a b").forEach { name ->
            assertFailsWith<IllegalArgumentException>(name) { FileCrossProcessLock(locks).withLock(name) {} }
        }
    }

    /** 0 when the lock was free (and is released again), else the errno of the refusal. */
    @OptIn(ExperimentalForeignApi::class)
    private fun tryLockFromAnotherDescriptor(path: String): Int {
        val descriptor = open(path, O_RDWR)
        check(descriptor >= 0) { "cannot open $path" }
        try {
            if (flock(descriptor, LOCK_EX or LOCK_NB) != 0) return errno
            flock(descriptor, LOCK_UN)
            return 0
        } finally {
            close(descriptor)
        }
    }

    private companion object {
        const val HOLDERS = 40
        var spaces = 0
    }
}
