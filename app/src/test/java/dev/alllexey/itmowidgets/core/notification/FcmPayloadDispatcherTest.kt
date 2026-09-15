package dev.alllexey.itmowidgets.core.notification

import com.google.gson.Gson
import com.google.gson.JsonElement
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class FcmPayloadDispatcherTest {
    @Test fun `routes independent types and ignores unknown malformed and missing payloads`() = runTest {
        val received = mutableListOf<String>()
        fun handler(name: String) = object : FcmPayloadHandler {
            override val type = name
            override suspend fun handle(payload: JsonElement) { received += name + payload.asJsonObject["id"].asInt }
        }
        val dispatcher = FcmPayloadDispatcher(Gson(), setOf(handler("first"), handler("second")))
        for (wire in listOf("{", "null", "{}", "[]", "", "{\"type\":\"first\"}",
            "{\"type\":\"first\",\"payload\":null}", "{\"type\":\"third\",\"payload\":{}}")) {
            dispatcher.dispatch(wire)
        }
        assertTrue(received.isEmpty())
        dispatcher.dispatch("""{"type":"second","payload":{"id":2}}""")
        dispatcher.dispatch("""{"type":"first","payload":{"id":1}}""")
        assertEquals(listOf("second2", "first1"), received)
    }

    @Test fun `one handler failure does not poison the next message and cancellation propagates`() = runTest {
        var fail = true
        var handled = false
        val handler = object : FcmPayloadHandler {
            override val type = "type"
            override suspend fun handle(payload: JsonElement) {
                if (fail) error("Synthetic failure")
                handled = true
            }
        }
        val dispatcher = FcmPayloadDispatcher(Gson(), setOf(handler))
        val wire = """{"type":"type","payload":{}}"""
        dispatcher.dispatch(wire)
        fail = false
        dispatcher.dispatch(wire)
        assertTrue(handled)
        val cancelled = object : FcmPayloadHandler {
            override val type = "type"
            override suspend fun handle(payload: JsonElement) { throw CancellationException() }
        }
        try {
            FcmPayloadDispatcher(Gson(), setOf(cancelled)).dispatch(wire)
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
    }
}
