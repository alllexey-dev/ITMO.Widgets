package dev.alllexey.itmowidgets.client.json

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** SP-02 case 13 (every date form 1.x reads) and case 14 (ISO dates), plus the UUID form. */
class WireSerializersTest {

    private fun instant(text: String): Instant =
        BackendJson.decodeFromJsonElement(WireInstantSerializer, JsonPrimitive(text))

    @Test
    fun readsEveryObservedDateTimeForm() {
        val noon = Instant.parse("2026-10-03T09:00:00Z")
        val forms = mapOf(
            "2026-10-03T12:00+03:00" to noon, // Gson FCM, `:00` seconds dropped
            "2026-10-03T12:00:42.123456789+03:00" to Instant.parse("2026-10-03T09:00:42.123456789Z"),
            "2026-10-03T12:00:00+03:00" to noon, // Jackson offset
            "2026-10-03T12:00:00.123456+03:00" to Instant.parse("2026-10-03T09:00:00.123456Z"),
            "2026-10-03T09:00:00Z" to noon,
            "2026-10-03T09:00Z" to noon, // Gson UTC, `:00` seconds dropped
            "2026-10-05T10:00:00+03:00" to Instant.parse("2026-10-05T07:00:00Z"),
        )

        forms.forEach { (text, expected) -> assertEquals(expected, instant(text), text) }
    }

    @Test
    fun writesUtc() {
        val encoded = BackendJson.encodeToString(WireInstantSerializer, Instant.parse("2026-10-03T12:00:00+03:00"))

        assertEquals("\"2026-10-03T09:00:00Z\"", encoded)
    }

    @Test
    fun rejectsDateTimeWithoutOffset() {
        assertFailsWith<SerializationException> { instant("2026-10-03T12:00:00") }
    }

    @Test
    fun localDateAndTimeAreIso() {
        assertEquals("\"2026-01-05\"", BackendJson.encodeToString(IsoLocalDateSerializer, LocalDate(2026, 1, 5)))
        assertEquals(LocalDate(2026, 1, 5), BackendJson.decodeFromString(IsoLocalDateSerializer, "\"2026-01-05\""))
        assertEquals(LocalTime(8, 20), BackendJson.decodeFromString(IsoLocalTimeSerializer, "\"08:20\""))
        assertEquals(LocalTime(8, 20), BackendJson.decodeFromString(IsoLocalTimeSerializer, "\"08:20:00\""))
        assertEquals("\"08:20\"", BackendJson.encodeToString(IsoLocalTimeSerializer, LocalTime(8, 20)))
        assertFailsWith<SerializationException> {
            BackendJson.decodeFromString(IsoLocalDateSerializer, "\"05.01.2026\"")
        }
    }

    @Test
    fun uuidIsHexDashString() {
        val text = "0b8f7e2c-3d4a-4c5b-9e6f-7a8b9c0d1e2f"

        val uuid = BackendJson.decodeFromString(UuidSerializer, "\"${text.uppercase()}\"")

        assertEquals(Uuid.parse(text), uuid)
        assertEquals("\"$text\"", BackendJson.encodeToString(UuidSerializer, uuid))
        assertFailsWith<SerializationException> { BackendJson.decodeFromString(UuidSerializer, "\"not-a-uuid\"") }
    }

    @Test
    fun nullNeverBecomesADeclaredDefault() {
        // SP-02 rows 6 and 7: without coerceInputValues a null for a defaulted non-null field fails.
        assertFailsWith<SerializationException> { BackendJson.decodeFromString(String.serializer(), "null") }
        assertFailsWith<SerializationException> {
            BackendJson.decodeFromString(Defaulted.serializer(), """{"audience":null}""")
        }
        assertFailsWith<SerializationException> {
            BackendJson.decodeFromString(Defaulted.serializer(), """{"audience":"CLASSMATES"}""")
        }
    }

    @kotlinx.serialization.Serializable
    private enum class Audience { ALL, FRIENDS, NOBODY }

    @kotlinx.serialization.Serializable
    private class Defaulted(val audience: Audience = Audience.ALL)
}
