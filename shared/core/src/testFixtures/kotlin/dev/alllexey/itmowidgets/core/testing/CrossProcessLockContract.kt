package dev.alllexey.itmowidgets.core.testing

import dev.alllexey.itmowidgets.core.storage.CrossProcessLock
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * What every [CrossProcessLock] guarantees, for a platform test to run on its lock with [checkAll]. Holders run on
 * real threads, so a lock that blocks its thread (a file lock) passes as well as one that suspends.
 */
object CrossProcessLockContract {

    /**
     * Runs every check in its own lock space: [freshLockSpace] makes one and returns its opener; every call of the
     * opener opens a handle on that space, as a second process would.
     */
    fun checkAll(freshLockSpace: () -> () -> CrossProcessLock): TestResult = runTest {
        withContext(Dispatchers.Default) {
            checks.forEach { (name, check) ->
                try {
                    check(freshLockSpace())
                } catch (failure: Throwable) {
                    throw AssertionError("CrossProcessLock contract: $name", failure)
                }
            }
        }
    }

    private val checks: Map<String, suspend (openLock: () -> CrossProcessLock) -> Unit> = mapOf(
        "withLock returns the action's result" to { openLock ->
            assertEquals(42, openLock().withLock(NAME) { 42 })
        },
        "a second holder of a name enters only after the first released it" to { openLock ->
            withContext(Dispatchers.Default) {
                val firstIn = CompletableDeferred<Unit>()
                val release = CompletableDeferred<Unit>()
                val holder = launch { openLock().withLock(NAME) { firstIn.complete(Unit); release.await() } }
                firstIn.await()
                val waiter = async { openLock().withLock(NAME) { release.isCompleted } }
                // Room for a broken lock to let the waiter in early.
                delay(WAIT)
                release.complete(Unit)
                assertTrue(waiter.await(), "The second holder entered while the first held the lock")
                holder.join()
            }
        },
        "another name does not wait" to { openLock ->
            withContext(Dispatchers.Default) {
                val held = CompletableDeferred<Unit>()
                val release = CompletableDeferred<Unit>()
                val holder = launch { openLock().withLock(NAME) { held.complete(Unit); release.await() } }
                held.await()
                assertEquals("entered", withTimeout(TIMEOUT) { openLock().withLock(OTHER_NAME) { "entered" } })
                release.complete(Unit)
                holder.join()
            }
        },
        "a failing action releases the lock" to { openLock ->
            assertFailsWith<IllegalStateException> { openLock().withLock(NAME) { error("failed inside") } }
            assertEquals("again", withTimeout(TIMEOUT) { openLock().withLock(NAME) { "again" } })
        },
    )

    private const val NAME = "contract-lock"
    private const val OTHER_NAME = "other-lock"
    private val WAIT = 100.milliseconds
    private val TIMEOUT = 5.seconds
}
