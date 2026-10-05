package dev.alllexey.itmowidgets.client.http

import dev.alllexey.itmowidgets.client.error.BackendException
import dev.alllexey.itmowidgets.client.support.MockBackend
import dev.alllexey.itmowidgets.client.support.Probe
import dev.alllexey.itmowidgets.client.support.ok
import dev.alllexey.itmowidgets.client.support.probeRoute
import dev.alllexey.itmowidgets.client.support.runSuspend
import kotlinx.serialization.SerializationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class EnvelopeDecodingTest {

    private fun backend(body: String) = MockBackend { ok(body) }

    @Test
    fun decodesDataAndIgnoresUnknownKeys() = runSuspend {
        val body =
            """{"success":true,"data":{"name":"Synthetic","isSaved":true,"extra":{"a":1}},"error":null,"meta":1}"""

        assertEquals(Probe("Synthetic"), backend(body).http.call(probeRoute(), Probe.serializer()))
    }

    @Test
    fun missingDataIsContractForValueCall() = runSuspend {
        for (body in listOf("""{"success":true}""", """{"success":true,"data":null}""")) {
            assertFailsWith<BackendException.Contract> { backend(body).http.call(probeRoute(), Probe.serializer()) }
        }
    }

    @Test
    fun unitCallAcceptsAnyData() = runSuspend {
        val bodies = listOf(
            """{"success":true}""",
            """{"success":true,"data":null}""",
            """{"success":true,"data":{}}""",
            """{"success":true,"data":"ok"}""",
        )
        for (body in bodies) {
            backend(body).http.callUnit(probeRoute())
        }
    }

    @Test
    fun successFalseOnOkIsContract() = runSuspend {
        val body = """{"success":false,"data":null,"error":{"message":"synthetic","code":"invalid_request"}}"""

        assertFailsWith<BackendException.Contract> { backend(body).http.callUnit(probeRoute()) }
        assertFailsWith<BackendException.Contract> { backend(body).http.call(probeRoute(), Probe.serializer()) }
    }

    @Test
    fun strictDecodeFailureIsContract() = runSuspend {
        val missingField = """{"success":true,"data":{"note":"no name"}}"""
        val wrongShape = """{"success":true,"data":["Synthetic"]}"""
        val missingSuccess = """{"data":{"name":"Synthetic"}}"""

        for (body in listOf(missingField, wrongShape, missingSuccess)) {
            val error = assertFailsWith<BackendException.Contract> {
                backend(body).http.call(probeRoute(), Probe.serializer())
            }
            assertIs<SerializationException>(error.cause)
        }
    }

    @Test
    fun brokenInvariantIsContract() = runSuspend {
        val error = assertFailsWith<BackendException.Contract> {
            backend("""{"success":true,"data":{"name":" "}}""").http.call(probeRoute(), Probe.serializer())
        }
        assertIs<IllegalArgumentException>(error.cause)
    }

    @Test
    fun nonJsonOkBodyIsContract() = runSuspend {
        assertFailsWith<BackendException.Contract> { backend("<html></html>").http.callUnit(probeRoute()) }
        assertFailsWith<BackendException.Contract> { backend("").http.callUnit(probeRoute()) }
    }
}
