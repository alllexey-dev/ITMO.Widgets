package dev.alllexey.itmowidgets.client.support

import dev.alllexey.itmowidgets.client.http.BackendRoute
import dev.alllexey.itmowidgets.client.http.jsonBody
import io.ktor.http.HttpMethod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** The test kit itself: the fixture loader, [assertJsonEquals] and request recording. */
class SupportTest {

    @Test
    fun loaderReadsACheckedInSample() {
        val text = Fixtures.read("support/user-data.json")

        assertTrue(text.contains("\"isu\": 100001"))
        assertTrue(text.contains("Тестовый Студент"), "UTF-8")
    }

    @Test
    fun jsonEqualityIgnoresKeyOrderAndAbsentNulls() {
        assertJsonEquals("""{"a":1,"b":[true,"x"],"c":null}""", """{ "b": [true, "x"], "a": 1 }""")
    }

    @Test
    fun jsonDateTimesAreEqualByInstant() {
        assertJsonEquals(
            """{"createdAt":"2026-10-03T12:00+03:00"}""",
            """{"createdAt":"2026-10-03T09:00:00Z"}""",
        )
        assertJsonEquals("""["2026-10-03T12:00:00.000+03:00"]""", """["2026-10-03T09:00Z"]""")
        assertNotJsonEquals("""{"createdAt":"2026-10-03T12:00+03:00"}""", """{"createdAt":"2026-10-03T12:00Z"}""")
    }

    @Test
    fun jsonEqualityIsExactOtherwise() {
        val unequal = listOf(
            """{"a":1}""" to """{"a":1.0}""",
            """{"a":1}""" to """{"a":"1"}""",
            """{"a":true}""" to """{"a":"true"}""",
            """{"a":"x"}""" to """{"a":"X"}""",
            """{"a":"2026-10-03"}""" to """{"a":"2026-10-03T00:00Z"}""",
            """{"a":"12:00"}""" to """{"a":"12:00:00"}""",
            """[1,2]""" to """[2,1]""",
            """{"a":1}""" to """{"a":1,"b":2}""",
            """{"a":null}""" to """{"a":0}""",
            """{"a":{}}""" to """{"a":[]}""",
        )
        for ((expected, actual) in unequal) {
            assertNotJsonEquals(expected, actual)
            assertNotJsonEquals(actual, expected)
        }
    }

    @Test
    fun recordsMethodPathQueryAndBody() = runSuspend {
        val request = recordRequest("probe") {
            http.callUnit(
                BackendRoute(
                    HttpMethod.Put,
                    listOf("api", "teachers", "summary levels"),
                    query = listOf("isu" to "1", "skipped" to null, "isu" to "2"),
                    body = jsonBody(Probe("Synthetic")),
                ),
            )
        }

        assertEquals(
            RecordedRequest(
                method = HttpMethod.Put,
                path = "/api/teachers/summary%20levels",
                query = listOf("isu" to "1", "isu" to "2"),
                body = """{"name":"Synthetic"}""",
            ),
            request,
        )
    }

    @Test
    fun recordingIgnoresTheAnswerButNotAMissingRequest() = runSuspend {
        val noBody = recordRequest("probe", response = """{"success":true,"data":{"name":""}}""") {
            http.call(probeRoute(), Probe.serializer())
        }
        assertEquals(RecordedRequest(HttpMethod.Get, "/api/probe", emptyList(), null), noBody)

        assertFailsWith<AssertionError> { RouteCase("silent") {}.record() }
    }

    private fun assertNotJsonEquals(expected: String, actual: String) {
        assertFailsWith<AssertionError>("$expected vs $actual") { assertJsonEquals(expected, actual) }
    }
}
