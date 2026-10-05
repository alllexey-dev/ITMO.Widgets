package dev.alllexey.itmowidgets.client.users

import dev.alllexey.itmowidgets.client.error.BackendException
import dev.alllexey.itmowidgets.client.friends.FriendsRouteCases
import dev.alllexey.itmowidgets.client.support.MockBackend
import dev.alllexey.itmowidgets.client.support.errorEnvelope
import dev.alllexey.itmowidgets.client.support.json
import dev.alllexey.itmowidgets.client.support.ok
import dev.alllexey.itmowidgets.client.support.record
import dev.alllexey.itmowidgets.client.support.runSuspend
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Instant

/** Port of Core 1.7.0 `weblogin/WebLoginApiTest`; `myRoles` is not mirrored (0 app call sites, the web reads it). */
class WebLoginApiTest {

    private val preview = WebLoginPreview(
        challengeId = UsersRouteCases.challengeId,
        userAgent = "Mozilla/5.0 (X11; Linux x86_64)",
        createdAt = Instant.parse("2026-09-24T10:00:00Z"),
        expiresAt = Instant.parse("2026-09-24T10:05:00Z"),
    )

    private class Call(
        val method: HttpMethod,
        val path: String,
        val data: String,
        val run: suspend MockBackend.() -> Unit,
    )

    @Test
    fun webLoginRoutesHaveAnExactVerbPathEmptyBodyAndTypedReply() = runSuspend {
        val calls = listOf(
            Call(
                HttpMethod.Get,
                "/api/users/me/web-login/K7M2QX9P",
                """{"challengeId":"${UsersRouteCases.challengeId}",
                "userAgent":"Mozilla/5.0 (X11; Linux x86_64)","createdAt":"2026-09-24T10:00:00Z",
                "expiresAt":"2026-09-24T10:05:00Z"}""",
            ) { assertEquals(preview, client.users.webLoginPreview("K7M2QX9P")) },
            Call(HttpMethod.Post, "/api/users/me/web-login/${UsersRouteCases.challengeId}/approve", "{}") {
                client.users.approveWebLogin(UsersRouteCases.challengeId)
            },
        )
        for (call in calls) {
            val backend = MockBackend { ok("""{"success":true,"data":${call.data},"error":null}""") }

            backend.(call.run)()

            val request = backend.lastRequest
            assertEquals(call.method, request.method, call.path)
            assertEquals(call.path, request.url.encodedPath)
            assertIs<OutgoingContent.NoContent>(request.body, call.path)
            assertNull(request.headers[HttpHeaders.Authorization])
        }
    }

    @Test
    fun rolesAreNotMirrored() = runSuspend {
        // 1.x "unknown future roles stay plain strings": Core 2.0 has no myRoles; CONTRACT.md lists the route.
        val paths = (UsersRouteCases.all + FriendsRouteCases.all).map { it.record().path }

        assertFalse("/api/users/me/roles" in paths)
    }

    @Test
    fun anExpiredOrUnknownCodeSurfacesAsNotFound() = runSuspend {
        val backend = MockBackend { json(HttpStatusCode.NotFound, errorEnvelope("not_found")) }

        val error = assertFailsWith<BackendException.NotFound> { backend.client.users.webLoginPreview("ZZZZZZZZ") }

        assertEquals("not_found", error.code)
        assertEquals("/api/users/me/web-login/ZZZZZZZZ", backend.lastRequest.url.encodedPath)
    }
}
