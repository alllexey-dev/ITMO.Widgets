package dev.alllexey.itmowidgets.client.http

import dev.alllexey.itmowidgets.client.contract.AllRouteCases
import dev.alllexey.itmowidgets.client.error.BackendException
import dev.alllexey.itmowidgets.client.support.MockBackend
import dev.alllexey.itmowidgets.client.support.probeRoute
import dev.alllexey.itmowidgets.client.support.runSuspend
import io.ktor.http.HttpHeaders
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Backend's texts (scope titles) follow the app's UI language, sent as `Accept-Language` on every request. */
class AcceptLanguageHeaderTest {

    @Test
    fun everyPublicFunctionSendsTheLanguage() = runSuspend {
        for (case in AllRouteCases.all) {
            val backend = MockBackend(language = { "ru" })
            try {
                case.call(backend.client)
            } catch (_: BackendException.Contract) {
                // The empty envelope fails every value call; only the request matters here.
            }

            assertEquals("ru", backend.lastRequest.headers[HttpHeaders.AcceptLanguage], case.name)
        }
    }

    @Test
    fun theLanguageIsAskedPerRequest() = runSuspend {
        var language = "ru"
        val backend = MockBackend(language = { language })

        backend.http.callUnit(probeRoute())
        language = "en"
        backend.http.callUnit(probeRoute())

        assertEquals(listOf("ru", "en"), backend.requests.map { it.headers[HttpHeaders.AcceptLanguage] })
    }

    @Test
    fun noOrABlankLanguageSendsNoHeader() = runSuspend {
        for (backend in listOf(MockBackend(), MockBackend(language = { " " }), MockBackend(language = { null }))) {
            backend.http.callUnit(probeRoute())

            assertNull(backend.lastRequest.headers[HttpHeaders.AcceptLanguage])
        }
    }
}
