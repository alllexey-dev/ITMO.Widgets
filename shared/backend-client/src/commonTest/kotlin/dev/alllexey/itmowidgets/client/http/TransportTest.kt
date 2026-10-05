package dev.alllexey.itmowidgets.client.http

import dev.alllexey.itmowidgets.client.error.BackendException
import dev.alllexey.itmowidgets.client.support.MockBackend
import dev.alllexey.itmowidgets.client.support.Probe
import dev.alllexey.itmowidgets.client.support.probeRoute
import dev.alllexey.itmowidgets.client.support.runSuspend
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import okio.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TransportTest {

    @Test
    fun engineIoFailureIsTransportWithTheCauseKept() = runSuspend {
        val backend = MockBackend { throw IOException("connection reset") }

        val error = assertFailsWith<BackendException.Transport> { backend.http.call(probeRoute(), Probe.serializer()) }

        // Coroutine stack-trace recovery may hand over a copy of the engine's exception, not the same instance.
        val cause = assertIs<IOException>(error.cause)
        assertEquals("connection reset", cause.message)
    }

    @Test
    fun engineCancellationPassesThrough() = runSuspend {
        val backend = MockBackend { throw CancellationException("engine stopped") }

        assertFailsWith<CancellationException> { backend.http.callUnit(probeRoute()) }
    }

    @Test
    fun cancellingTheCallerCancelsTheCall() = runSuspend {
        val reached = CompletableDeferred<Unit>()
        val backend = MockBackend {
            reached.complete(Unit)
            awaitCancellation()
        }
        val call = async(start = CoroutineStart.UNDISPATCHED) { backend.http.callUnit(probeRoute()) }
        reached.await()

        call.cancel()

        assertFailsWith<CancellationException> { call.await() }
        assertTrue(call.isCancelled)
    }
}
