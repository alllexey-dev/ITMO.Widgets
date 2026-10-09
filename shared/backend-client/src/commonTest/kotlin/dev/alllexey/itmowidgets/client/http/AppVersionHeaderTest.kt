package dev.alllexey.itmowidgets.client.http

import dev.alllexey.itmowidgets.client.ClientVersion
import dev.alllexey.itmowidgets.client.contract.AllRouteCases
import dev.alllexey.itmowidgets.client.error.BackendException
import dev.alllexey.itmowidgets.client.support.MockBackend
import dev.alllexey.itmowidgets.client.support.probeRoute
import dev.alllexey.itmowidgets.client.support.runSuspend
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** CO-VER1: every Backend request names the app build in `X-App-Version`; other clients on the engine do not. */
class AppVersionHeaderTest {

    private val github = ClientVersion("2.3.0-beta.1", "20291", "android", "github")

    @Test
    fun formatsTheVersionBuildPlatformAndDistribution() {
        assertEquals("2.3.0-beta.1 (20291); android; github", github.headerValue)
        assertEquals(
            "2.3.0-beta.1 (20291); android; play",
            ClientVersion("2.3.0-beta.1", "20291", "android", "play").headerValue,
        )
        assertEquals("2.3 (7); ios; appstore", ClientVersion("2.3", "7", "ios", "appstore").headerValue)
    }

    @Test
    fun keepsTheValueOneParseableAsciiHeader() {
        val version = ClientVersion("2.3 \u03b2;(x)", "\n", "ios", "dev")

        assertEquals("2.3x (unknown); ios; dev", version.headerValue)
    }

    @Test
    fun everyPublicFunctionSendsTheVersion() = runSuspend {
        for (case in AllRouteCases.all) {
            val backend = MockBackend(version = github)
            try {
                case.call(backend.client)
            } catch (_: BackendException.Contract) {
                // The empty envelope fails every value call; only the request matters here.
            }

            assertEquals(github.headerValue, backend.lastRequest.headers[ClientVersion.HEADER], case.name)
        }
    }

    @Test
    fun noVersionSendsNoHeader() = runSuspend {
        val backend = MockBackend()

        backend.http.callUnit(probeRoute())

        assertNull(backend.lastRequest.headers[ClientVersion.HEADER])
    }

    @Test
    fun anotherClientOnTheSameEngineDoesNotSendIt() = runSuspend {
        val backend = MockBackend(version = github)
        val other = HttpClient(backend.engine)

        backend.http.callUnit(probeRoute())
        other.get("https://my.itmo.test/api/probe")

        assertEquals("2.3.0-beta.1 (20291); android; github", backend.requests[0].headers[ClientVersion.HEADER])
        assertNull(backend.requests[1].headers[ClientVersion.HEADER])
    }
}
