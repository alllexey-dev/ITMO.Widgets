package dev.alllexey.itmowidgets.core.presentation

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BusyKeysTest {

    @Test
    fun `a busy row ignores a second tap until its action completes`() = runTest {
        val keys = BusyKeys<Int>(backgroundScope)
        val gate = CompletableDeferred<Unit>()
        var runs = 0

        keys.launch(1) { runs++; gate.await() }
        assertNull(keys.launch(1) { runs++ })
        keys.launch(2) { runs++ }
        assertTrue(1 in keys)
        runCurrent()

        assertEquals(2, runs)
        assertEquals(setOf(1), keys.busy.value)
        gate.complete(Unit)
        runCurrent()
        assertEquals(emptySet<Int>(), keys.busy.value)
    }

    @Test
    fun `a key is released when its action fails`() = runTest {
        val failures = mutableListOf<Any>()
        val scope = CoroutineScope(
            SupervisorJob() + StandardTestDispatcher(testScheduler) + CoroutineExceptionHandler { _, e -> failures += e }
        )
        val keys = BusyKeys<Int>(scope)

        keys.launch(7) { error("Backend refused") }
        assertEquals(setOf(7), keys.busy.value)
        runCurrent()

        assertEquals(1, failures.size)
        assertFalse(7 in keys)
        var retried = false
        keys.launch(7) { retried = true }
        runCurrent()
        assertTrue(retried)
        scope.cancel()
    }

    @Test
    fun `a key is released when its action is cancelled`() = runTest {
        val keys = BusyKeys<Int>(backgroundScope)
        val job = keys.launch(3) { CompletableDeferred<Unit>().await() }
        runCurrent()

        job?.cancel()
        runCurrent()

        assertEquals(emptySet<Int>(), keys.busy.value)
    }
}
