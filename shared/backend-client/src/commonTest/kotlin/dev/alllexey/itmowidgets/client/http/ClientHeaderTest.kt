package dev.alllexey.itmowidgets.client.http

import dev.alllexey.itmowidgets.client.BuildIdentity
import dev.alllexey.itmowidgets.client.ClientHeader
import dev.alllexey.itmowidgets.client.contract.AllRouteCases
import dev.alllexey.itmowidgets.client.error.BackendException
import dev.alllexey.itmowidgets.client.support.MockBackend
import dev.alllexey.itmowidgets.client.support.probeRoute
import dev.alllexey.itmowidgets.client.support.runSuspend
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** ADR 0034: every Backend request names the client in `X-Client`, next to `X-App-Version`. */
class ClientHeaderTest {

    private val play = ClientHeader("android", "2.3.0", "20300", BuildIdentity("play"), "36") { INSTALL }

    @Test
    fun formatsThePairsInTheirOrder() = runSuspend {
        val custom = ClientHeader(
            kind = "ios",
            version = "2.3.0",
            build = "20300",
            identity = BuildIdentity("custom", source = "someone/fork", commit = "0123abcd4567", base = "2.2.1"),
            os = "18",
        ) { INSTALL }

        assertEquals("kind=android; version=2.3.0; build=20300; channel=play; os=36; install=$INSTALL", play.value())
        assertEquals(
            "kind=ios; version=2.3.0; build=20300; channel=custom; source=someone/fork; commit=0123abcd4567; " +
                "base=2.2.1; os=18; install=$INSTALL",
            custom.value(),
        )
    }

    @Test
    fun keepsOnlyTheAllowedCharactersOfAValue() {
        assertEquals(
            "version=2.3.0-beta.1+x; source=a/b_c",
            ClientHeader.format(listOf("version" to "2.3.0-beta.1 \u03b2;+x=", "source" to "a/b_c!@#")),
        )
    }

    @Test
    fun leavesOutNullAndEmptyValues() = runSuspend {
        val header = ClientHeader("android", "2.3.0", " ;", BuildIdentity("dev", source = "", commit = null), "36")

        assertEquals("kind=android; version=2.3.0; channel=dev; os=36", header.value())
    }

    @Test
    fun truncatesAValueTo64Characters() {
        assertEquals("commit=" + "a".repeat(64), ClientHeader.format(listOf("commit" to "a".repeat(100))))
    }

    @Test
    fun leavesOutAPairThatWouldPassTheLengthCap() {
        val long = "x".repeat(64)
        val pairs = listOf("kind" to "android", "version" to "2.3.0") +
            (1..6).map { "k$it" to long } + ("toolong" to long) + ("install" to INSTALL)

        val header = ClientHeader.format(pairs)

        assertTrue(header.length <= ClientHeader.MAX_LENGTH, header)
        assertTrue(header.startsWith("kind=android; version=2.3.0; k1="), header)
        assertFalse("toolong" in header, header)
        assertTrue(header.endsWith("; k6=$long; install=$INSTALL"), header)
    }

    @Test
    fun aFailingInstallIdLeavesInstallOut() = runSuspend {
        val header = ClientHeader("ios", "2.3.0", "1", BuildIdentity("appstore"), "18") { error("no store") }

        assertEquals("kind=ios; version=2.3.0; build=1; channel=appstore; os=18", header.value())
    }

    @Test
    fun everyPublicFunctionSendsTheHeader() = runSuspend {
        for (case in AllRouteCases.all) {
            val backend = MockBackend(client = play)
            try {
                case.call(backend.client)
            } catch (_: BackendException.Contract) {
                // The empty envelope fails every value call; only the request matters here.
            }

            assertEquals(play.value(), backend.lastRequest.headers[ClientHeader.HEADER], case.name)
        }
    }

    @Test
    fun asksForTheInstallIdOnEveryRequest() = runSuspend {
        var install = "first"
        val header = ClientHeader("android", "2.3.0", "20300", BuildIdentity("play"), "36") { install }
        val backend = MockBackend(client = header)

        backend.http.callUnit(probeRoute())
        install = "second"
        backend.http.callUnit(probeRoute())

        assertTrue(backend.requests[0].headers[ClientHeader.HEADER]!!.endsWith("install=first"))
        assertTrue(backend.requests[1].headers[ClientHeader.HEADER]!!.endsWith("install=second"))
    }

    @Test
    fun noClientSendsNoHeader() = runSuspend {
        val backend = MockBackend()

        backend.http.callUnit(probeRoute())

        assertNull(backend.lastRequest.headers[ClientHeader.HEADER])
    }

    private companion object {
        const val INSTALL = "0b6c3c2e-5f1a-4d8e-9a7b-2c4d6e8f0a1b"
    }
}
