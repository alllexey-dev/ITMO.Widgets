package dev.alllexey.itmowidgets.feature.recordbook.data.lms

import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.testing.FakeDemoMode
import dev.alllexey.itmowidgets.feature.recordbook.MainDispatcherRule
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LmsJournalClientTest {
    @get:Rule val main = MainDispatcherRule()

    @Test fun `reads every page of a journal using LMS points`() = runTest {
        val paths = mutableListOf<String>()
        val engine = MockEngine { request ->
            assertEquals("api.tuxedo.se.ifmo.ru", request.url.host)
            assertEquals("Bearer synthetic-token", request.headers[HttpHeaders.Authorization])
            paths += request.url.encodedPath
            when (request.url.encodedPath) {
                "/api/v1/student-journal/overall" -> respond("""{"courseGradeId":3}""")
                else -> {
                    val page = request.url.parameters["pageNumber"]!!.toInt()
                    val id = page + 1
                    respond("""{"elementsTotal":2,"content":[{"module":{"id":$id,"name":"РК $id"},"earnedPoints":9,"minGrade":0,"maxGrade":10,"percentGrade":90}]}""")
                }
            }
        }
        val client = LmsJournalClient(lmsHttpClient(engine), FakeDemoMode(), main.appDispatchers)
        val result = client.modules("synthetic-token", 15, 12) as AppResult.Success
        assertEquals(listOf(9.0, 9.0), result.value.map { it.earnedPoints })
        assertEquals(3, paths.size)
    }

    @Test fun `demo and malformed credentials never issue a request`() = runTest {
        var requests = 0
        val engine = MockEngine { requests++; respond("") }
        val demo = FakeDemoMode(true)
        val client = LmsJournalClient(lmsHttpClient(engine), demo, main.appDispatchers)
        assertEquals(AppResult.Failure(AppError.DemoUnavailable), client.modules("token", 1, 1))
        demo.active.value = false
        assertEquals(AppResult.Failure(AppError.Unauthorized), client.modules("bad\ntoken", 1, 1))
        assertEquals(0, requests)
    }

    @Test fun `a redirect is not followed with the bearer token`() = runTest {
        var requests = 0
        val engine = MockEngine {
            requests++
            respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "https://example.org/"))
        }
        val client = LmsJournalClient(lmsHttpClient(engine), FakeDemoMode(), main.appDispatchers)
        assertTrue(client.modules("token", 1, 1) is AppResult.Failure)
        assertEquals(1, requests)
    }

    @Test fun `unexpected journal response is not treated as an empty list`() = runTest {
        val engine = MockEngine { respond("{}") }
        val client = LmsJournalClient(lmsHttpClient(engine), FakeDemoMode(), main.appDispatchers)
        assertEquals(AppResult.Failure(AppError.Unknown()), client.modules("token", 1, 1))
    }

    @Test fun `expired session remains distinct from an empty journal`() = runTest {
        val engine = MockEngine { respond("", HttpStatusCode.Unauthorized) }
        val client = LmsJournalClient(lmsHttpClient(engine), FakeDemoMode(), main.appDispatchers)
        assertEquals(AppResult.Failure(AppError.Unauthorized), client.modules("token", 1, 1))
    }
    @Test fun `discovers enrolled programs and their courses`() = runTest {
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/api/v1/programs/by-student" -> respond("""{"elementsTotal":1,"content":[{"program":{"id":15,"name":"Программа"}}]}""")
                "/api/v1/programs/15/courses/by-student" -> respond("""[{"id":12,"name":"Веб-программирование"}]""")
                else -> error("Unexpected path")
            }
        }
        val client = LmsJournalClient(lmsHttpClient(engine), FakeDemoMode(), main.appDispatchers)
        assertEquals(listOf(LmsProgram(15, "Программа")), (client.programs("token") as AppResult.Success).value)
        assertEquals(listOf(LmsCourse(12, "Веб-программирование")), (client.courses("token", 15) as AppResult.Success).value)
    }

    @Test fun `exchanges authorization code for access token and blocks demo mode`() = runTest {
        var requests = 0
        val engine = MockEngine { request ->
            requests++
            assertEquals("api.tuxedo.se.ifmo.ru", request.url.host)
            assertEquals("/api/v1/sso/token", request.url.encodedPath)
            respond("""{"accessToken":"synthetic-access"}""")
        }
        val demo = FakeDemoMode(true)
        val client = LmsJournalClient(lmsHttpClient(engine), demo, main.appDispatchers)
        assertEquals(AppResult.Failure(AppError.DemoUnavailable), client.exchangeToken("synthetic-code", "synthetic-state"))
        assertEquals(0, requests)
        demo.active.value = false
        assertEquals(AppResult.Success("synthetic-access"), client.exchangeToken("synthetic-code", "synthetic-state"))
        assertEquals(1, requests)
    }

}
