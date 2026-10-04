package dev.alllexey.itmowidgets.core.presentation

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RefreshTrackerTest {

    private val calls = mutableListOf<RefreshMode>()
    private val gate = CompletableDeferred<Unit>()

    private val awaitGate: suspend (RefreshMode) -> Unit = { mode ->
        calls += mode
        gate.await()
    }

    @Test
    fun `requests during a refresh join it instead of starting another`() = runTest {
        val tracker = RefreshTracker(backgroundScope)

        val first = tracker.launch(RefreshMode.Silent, awaitGate)
        val second = tracker.launch(RefreshMode.Silent, awaitGate)
        val pull = tracker.launch(RefreshMode.Pull, awaitGate)
        runCurrent()

        assertEquals(listOf(RefreshMode.Silent), calls)
        assertSame(first, second)
        assertSame(first, pull)
        gate.complete(Unit)
        runCurrent()
        assertTrue(first.isCompleted)
    }

    @Test
    fun `a silent refresh shows no indicator and a joining pull raises it until the end`() = runTest {
        val tracker = RefreshTracker(backgroundScope)

        tracker.launch(RefreshMode.Silent, awaitGate)
        runCurrent()
        assertFalse(tracker.refreshing.value)

        tracker.launch(RefreshMode.Pull, awaitGate)
        assertTrue(tracker.refreshing.value)

        gate.complete(Unit)
        runCurrent()
        assertFalse(tracker.refreshing.value)
    }

    @Test
    fun `force replaces an in-flight silent refresh and starts after it has stopped`() = runTest {
        val tracker = RefreshTracker(backgroundScope)
        val forced = CompletableDeferred<Unit>()
        val silent = tracker.launch(RefreshMode.Silent, awaitGate)
        runCurrent()

        val force = tracker.launch(RefreshMode.Force) { mode ->
            assertTrue("the replaced refresh must be finished first", silent.isCompleted)
            calls += mode
            forced.await()
        }
        assertTrue(tracker.refreshing.value)
        runCurrent()

        assertTrue(silent.isCancelled)
        assertEquals(listOf(RefreshMode.Silent, RefreshMode.Force), calls)
        assertTrue("the cancelled run must not clear the indicator of its replacement", tracker.refreshing.value)
        assertSame(force, tracker.launch(RefreshMode.Force, awaitGate))
        assertSame(force, tracker.launch(RefreshMode.Pull, awaitGate))

        forced.complete(Unit)
        runCurrent()
        assertFalse(tracker.refreshing.value)
        assertEquals(listOf(RefreshMode.Silent, RefreshMode.Force), calls)
    }

    @Test
    fun `a failed refresh releases the tracker for the next request`() = runTest {
        val failures = mutableListOf<Any>()
        val scope = failingScope(failures)
        val tracker = RefreshTracker(scope)

        tracker.launch(RefreshMode.Pull) { error("offline") }
        assertTrue(tracker.refreshing.value)
        runCurrent()

        assertFalse(tracker.refreshing.value)
        assertEquals(1, failures.size)
        val next = tracker.launch(RefreshMode.Silent, awaitGate)
        runCurrent()
        assertEquals(listOf(RefreshMode.Silent), calls)
        gate.complete(Unit)
        runCurrent()
        assertTrue(next.isCompleted)
        scope.cancel()
    }

    private fun TestScope.failingScope(failures: MutableList<Any>) = CoroutineScope(
        SupervisorJob() + StandardTestDispatcher(testScheduler) + CoroutineExceptionHandler { _, e -> failures += e }
    )
}
