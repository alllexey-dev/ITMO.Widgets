package dev.alllexey.itmowidgets.feature.recordbook.data.lms

import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.session.CurrentUser
import dev.alllexey.itmowidgets.core.session.CurrentUserProvider
import dev.alllexey.itmowidgets.core.testing.FakeDemoMode
import dev.alllexey.itmowidgets.feature.recordbook.domain.lms.LmsLoadResult
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.util.Base64
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidLmsGatewayTest {

    private fun userProvider(isu: Int? = 123456) = object : CurrentUserProvider {
        override suspend fun getCurrentUser(): CurrentUser? = isu?.let { CurrentUser(it, null, null) }
    }

    private fun syntheticJwt(isu: String): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val header = encoder.encodeToString("""{"alg":"none"}""".toByteArray())
        val payload = encoder.encodeToString("""{"preferred_username":"$isu"}""".toByteArray())
        return "$header.$payload.sig"
    }

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, "application/json")

    @Test fun `demo never reaches the network or the account`() = runTest {
        val provider = object : CurrentUserProvider {
            override suspend fun getCurrentUser(): CurrentUser? = error("Demo must not read an account")
        }
        val engine = MockEngine { error("Demo must not hit the network") }
        val dispatcher = StandardTestDispatcher(testScheduler)
        val gateway = AndroidLmsGateway(FakeDemoMode(true), provider, AppDispatchers(dispatcher, dispatcher, dispatcher), engine)
        assertEquals(AppError.DemoUnavailable, gateway.canStart())
        assertEquals(
            LmsLoadResult.Failed(AppError.DemoUnavailable),
            gateway.load("synthetic-code", "synthetic-state", "Программирование", "2026/2027", 3),
        )
    }

    @Test fun `allowed browser url rejects userinfo, non-https, custom ports and unknown hosts`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val gateway = AndroidLmsGateway(
            FakeDemoMode(),
            userProvider(),
            AppDispatchers(dispatcher, dispatcher, dispatcher),
            MockEngine { respond("") },
        )
        assertTrue(gateway.isAllowedBrowserUrl("https://id.itmo.ru/auth"))
        assertTrue(gateway.isAllowedBrowserUrl("https://sso.tuxedo.se.ifmo.ru/realms/Newdex/protocol/openid-connect/auth"))
        assertTrue(gateway.isAllowedBrowserUrl("https://lms.tuxedo.se.ifmo.ru/sso/token?code=1"))
        assertFalse(gateway.isAllowedBrowserUrl("https://user:pass@lms.tuxedo.se.ifmo.ru/sso/token"))
        assertFalse(gateway.isAllowedBrowserUrl("http://lms.tuxedo.se.ifmo.ru/sso/token"))
        assertFalse(gateway.isAllowedBrowserUrl("https://lms.tuxedo.se.ifmo.ru:8443/sso/token"))
        assertFalse(gateway.isAllowedBrowserUrl("https://evil.example.org/"))
    }

    @Test fun `returns Failed Forbidden when token belongs to another ISU`() = runTest {
        val paths = mutableListOf<String>()
        val token = syntheticJwt("999999")
        val engine = MockEngine { request ->
            paths += request.url.encodedPath
            respond("""{"accessToken":"$token"}""", HttpStatusCode.OK, jsonHeaders())
        }
        val dispatcher = StandardTestDispatcher(testScheduler)
        val gateway = AndroidLmsGateway(FakeDemoMode(), userProvider(123456), AppDispatchers(dispatcher, dispatcher, dispatcher), engine)
        val result = gateway.load("code", "state", "Программирование", "2026/2027", 3)
        assertEquals(LmsLoadResult.Failed(AppError.Forbidden), result)
        assertEquals(listOf("/api/v1/sso/token"), paths)
    }

    @Test fun `returns EmptyCourses when student has no LMS courses`() = runTest {
        val token = syntheticJwt("123456")
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/api/v1/sso/token" -> respond("""{"accessToken":"$token"}""", HttpStatusCode.OK, jsonHeaders())
                "/api/v1/programs/by-student" -> respond("""{"elementsTotal":1,"content":[{"program":{"id":15,"name":"ПИ 2026/2027"}}]}""", HttpStatusCode.OK, jsonHeaders())
                "/api/v1/programs/15/courses/by-student" -> respond("[]", HttpStatusCode.OK, jsonHeaders())
                else -> error("Unexpected path ${request.url.encodedPath}")
            }
        }
        val dispatcher = StandardTestDispatcher(testScheduler)
        val gateway = AndroidLmsGateway(FakeDemoMode(), userProvider(123456), AppDispatchers(dispatcher, dispatcher, dispatcher), engine)
        assertEquals(LmsLoadResult.EmptyCourses, gateway.load("code", "state", "Программирование", "2026/2027", 3))
    }

    @Test fun `returns NoMatch without requesting course journal when no course matches`() = runTest {
        val paths = mutableListOf<String>()
        val token = syntheticJwt("123456")
        val engine = MockEngine { request ->
            paths += request.url.encodedPath
            when (request.url.encodedPath) {
                "/api/v1/sso/token" -> respond("""{"accessToken":"$token"}""", HttpStatusCode.OK, jsonHeaders())
                "/api/v1/programs/by-student" -> respond("""{"elementsTotal":1,"content":[{"program":{"id":15,"name":"ПИ 2026/2027"}}]}""", HttpStatusCode.OK, jsonHeaders())
                "/api/v1/programs/15/courses/by-student" -> respond("""[{"id":12,"name":"Физика"}]""", HttpStatusCode.OK, jsonHeaders())
                else -> error("Must not fetch overall/modules when unmatched: ${request.url.encodedPath}")
            }
        }
        val dispatcher = StandardTestDispatcher(testScheduler)
        val gateway = AndroidLmsGateway(FakeDemoMode(), userProvider(123456), AppDispatchers(dispatcher, dispatcher, dispatcher), engine)
        assertEquals(LmsLoadResult.NoMatch, gateway.load("code", "state", "Программирование", "2026/2027", 3))
        assertFalse(paths.any { it.contains("student-journal") })
    }

    @Test fun `returns CourseFailed when modules request fails for matched course`() = runTest {
        val token = syntheticJwt("123456")
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/api/v1/sso/token" -> respond("""{"accessToken":"$token"}""", HttpStatusCode.OK, jsonHeaders())
                "/api/v1/programs/by-student" -> respond("""{"elementsTotal":1,"content":[{"program":{"id":15,"name":"ПИ 2026/2027"}}]}""", HttpStatusCode.OK, jsonHeaders())
                "/api/v1/programs/15/courses/by-student" -> respond("""[{"id":12,"name":"Программирование"}]""", HttpStatusCode.OK, jsonHeaders())
                "/api/v1/student-journal/overall" -> respond("", HttpStatusCode.InternalServerError)
                else -> error("Unexpected path ${request.url.encodedPath}")
            }
        }
        val dispatcher = StandardTestDispatcher(testScheduler)
        val gateway = AndroidLmsGateway(FakeDemoMode(), userProvider(123456), AppDispatchers(dispatcher, dispatcher, dispatcher), engine)
        val result = gateway.load("code", "state", "Программирование", "2026/2027", 3)
        assertTrue(result is LmsLoadResult.CourseFailed)
    }

    @Test fun `loads module scores for matched course`() = runTest {
        val token = syntheticJwt("123456")
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/api/v1/sso/token" -> respond("""{"accessToken":"$token"}""", HttpStatusCode.OK, jsonHeaders())
                "/api/v1/programs/by-student" -> respond("""{"elementsTotal":1,"content":[{"program":{"id":15,"name":"ПИ 2026/2027"}}]}""", HttpStatusCode.OK, jsonHeaders())
                "/api/v1/programs/15/courses/by-student" -> respond("""[{"id":12,"name":"Программирование"}]""", HttpStatusCode.OK, jsonHeaders())
                "/api/v1/student-journal/overall" -> respond("""{"courseGradeId":3}""", HttpStatusCode.OK, jsonHeaders())
                "/api/v1/student-journal/modules" -> respond(
                    """{"elementsTotal":1,"content":[{"module":{"id":1,"name":"РК №1"},"earnedPoints":9.0,"minGrade":0,"maxGrade":10.0,"percentGrade":90,"verificationInfo":{"effectiveOfflineProctoringStudentStatus":"VERIFIED"}}]}""",
                    HttpStatusCode.OK,
                    jsonHeaders(),
                )
                else -> error("Unexpected path ${request.url.encodedPath}")
            }
        }
        val dispatcher = StandardTestDispatcher(testScheduler)
        val gateway = AndroidLmsGateway(FakeDemoMode(), userProvider(123456), AppDispatchers(dispatcher, dispatcher, dispatcher), engine)
        val result = gateway.load("code", "state", "Программирование", "2026/2027", 3) as LmsLoadResult.Success
        assertEquals(1, result.scores.size)
        assertEquals("РК №1", result.scores.single().name)
        assertEquals(9.0, result.scores.single().earned ?: 0.0, 0.001)
    }
}
