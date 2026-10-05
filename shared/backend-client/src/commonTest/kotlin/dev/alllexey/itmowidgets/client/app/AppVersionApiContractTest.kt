package dev.alllexey.itmowidgets.client.app

import dev.alllexey.itmowidgets.client.error.BackendException
import dev.alllexey.itmowidgets.client.json.BackendJson
import dev.alllexey.itmowidgets.client.support.MockBackend
import dev.alllexey.itmowidgets.client.support.assertJsonEquals
import dev.alllexey.itmowidgets.client.support.errorEnvelope
import dev.alllexey.itmowidgets.client.support.json
import dev.alllexey.itmowidgets.client.support.ok
import dev.alllexey.itmowidgets.client.support.record
import dev.alllexey.itmowidgets.client.support.runSuspend
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * Port of Core 1.7.0 `AppVersionApiContractTest`. Where 2.0 differs it asserts the new behaviour: the legacy
 * `GET /api/app/version` is not mirrored, and `success=false` is a [BackendException.Contract] on 200 (an error
 * status is a typed exception with Backend's code) instead of an `ApiResponse` with `null` data.
 */
class AppVersionApiContractTest {

    private fun decode(text: String) = BackendJson.decodeFromString(AppVersionInfo.serializer(), text)

    private fun encode(value: AppVersionInfo) = BackendJson.encodeToString(AppVersionInfo.serializer(), value)

    private fun validWith(field: String, value: String?): String {
        val members = BackendJson.parseToJsonElement(VALID_INFO).jsonObject.toMutableMap()
        if (value == null) members.remove(field) else members[field] = BackendJson.parseToJsonElement(value)
        return JsonObject(members).toString()
    }

    @Test
    fun versionInfoRoundTripsExactRequiredFieldsWithAnEmptyNote() {
        val version = AppVersionInfo(minVersion = "2.1", latestVersion = "2.3", note = "")
        val json = encode(version)

        assertJsonEquals("""{"minVersion":"2.1","latestVersion":"2.3","note":""}""", json)
        assertEquals(version, decode(json))
    }

    @Test
    fun notePreservesUnicodeNewlinesAndMarkupAsLiteralText() {
        val note = "Обновление ИТМО \u2014 расписание и спорт.\n<b>Это обычный текст</b> & не HTML.\n日本語 \uD83D\uDE42"
        val version = AppVersionInfo(minVersion = "2.1", latestVersion = "2.1.1", note = note)

        val json = encode(version)
        val restored = decode(json)

        assertEquals(note, BackendJson.parseToJsonElement(json).jsonObject.getValue("note").jsonPrimitive.content)
        assertEquals(version, restored)
        assertEquals(note, restored.note)
    }

    @Test
    fun eachMissingVersionInfoFieldIsRejectedWithoutFabricatedDefaults() {
        for (field in FIELDS) {
            assertFailsWith<SerializationException>("Missing required field: $field") { decode(validWith(field, null)) }
        }
        assertFailsWith<SerializationException> { decode("{}") }
    }

    @Test
    fun eachNullOrNonStringVersionInfoFieldIsRejected() {
        for (field in FIELDS) {
            for (value in listOf("null", "42", "true", "{}", "[]")) {
                assertFailsWith<SerializationException>("Invalid $field value: $value") {
                    decode(validWith(field, value))
                }
            }
        }
    }

    @Test
    fun additionalResponseFieldsAreIgnoredWhileRequiredStringsStayUnchanged() {
        val json = validWith("futureMetadata", """{"values":[true,42,"new"]}""")

        assertEquals(AppVersionInfo("2.1", "2.3", ""), decode(json))
    }

    @Test
    fun typedVersionInfoAndLegacyVersionUseSeparateUnauthenticatedGetRoutesAndResponseShapes() = runSuspend {
        // 2.0 mirrors only the typed route; the legacy string route stays for 2.0.x clients and no route case calls it.
        val backend = MockBackend { ok("""{"success":true,"data":$VALID_INFO,"error":null}""") }

        val info = backend.client.app.versionInfo(null)

        assertEquals(AppVersionInfo("2.1", "2.3", ""), info)
        val request = backend.lastRequest
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("/api/app/version-info", request.url.encodedPath)
        assertEquals(0L, request.body.contentLength ?: 0L)
        assertNull(request.headers[HttpHeaders.Authorization])
        for (case in AppRouteCases.all) assertEquals("/api/app/version-info", case.record().path, case.name)
    }

    @Test
    fun failureResponseMayContainNullVersionDataWithoutViolatingStrictFieldParsing() = runSuspend {
        val failure = """{"success":false,"data":null,"error":{"message":"synthetic message","code":"unavailable"}}"""

        assertFailsWith<BackendException.Contract> { MockBackend { ok(failure) }.client.app.versionInfo(null) }

        val backend = MockBackend { json(HttpStatusCode.ServiceUnavailable, errorEnvelope("unavailable")) }
        val error = assertFailsWith<BackendException.Http> { backend.client.app.versionInfo(null) }
        assertEquals(503, error.status)
        assertEquals("unavailable", error.code)
        assertEquals("/api/app/version-info", backend.lastRequest.url.encodedPath)
        assertNull(backend.lastRequest.headers[HttpHeaders.Authorization])
    }

    private companion object {
        val FIELDS = listOf("minVersion", "latestVersion", "note")
        const val VALID_INFO = """{"minVersion":"2.1","latestVersion":"2.3","note":""}"""
    }
}
