package dev.alllexey.itmowidgets.client.users

import dev.alllexey.itmowidgets.client.json.BackendJson
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Port of Core 1.7.0 `weblogin/WebLoginContractTest`; a duplicate key keeps its last value by 07 Q4 (b). */
class WebLoginContractTest {

    private val preview = WebLoginPreview(
        challengeId = Uuid.parse("00000000-0000-0000-0000-000000000077"),
        userAgent = "Mozilla/5.0 (X11; Linux x86_64)",
        createdAt = Instant.parse("2026-09-24T10:00:00Z"),
        expiresAt = Instant.parse("2026-09-24T10:05:00Z"),
    )

    private fun encode(value: WebLoginPreview) = BackendJson.encodeToString(WebLoginPreview.serializer(), value)

    private fun decode(text: String) = BackendJson.decodeFromString(WebLoginPreview.serializer(), text)

    private fun tree(): JsonObject = BackendJson.parseToJsonElement(encode(preview)).jsonObject

    private fun JsonObject.with(name: String, value: JsonElement?): String =
        JsonObject(if (value == null) this - name else this + (name to value)).toString()

    @Test
    fun previewRoundTripsWithAndWithoutAUserAgent() {
        for (value in listOf(preview, preview.copy(userAgent = null))) {
            assertEquals(value, decode(encode(value)))
        }
    }

    @Test
    fun previewUsesTheExactWireNamesAndDecodesBackendInstants() {
        assertEquals(setOf("challengeId", "userAgent", "createdAt", "expiresAt"), tree().keys)
        val wire = """{"challengeId":"00000000-0000-0000-0000-000000000077",
            "userAgent":"Mozilla/5.0 (X11; Linux x86_64)",
            "createdAt":"2026-09-24T10:00:00.123456Z","expiresAt":"2026-09-24T10:05:00Z"}"""

        assertEquals(preview.copy(createdAt = Instant.parse("2026-09-24T10:00:00.123456Z")), decode(wire))
    }

    @Test
    fun userAgentDecodesFromNullAndAbsence() {
        val expected = preview.copy(userAgent = null)

        assertEquals(expected, decode(tree().with("userAgent", JsonNull)))
        assertEquals(expected, decode(tree().with("userAgent", null)))
    }

    @Test
    fun requiredFieldsNeverBecomeDefaults() {
        for (name in listOf("challengeId", "createdAt", "expiresAt")) {
            for (value in listOf(null, JsonNull, JsonPrimitive(1))) {
                val text = tree().with(name, value)
                assertFailsWith<SerializationException>("$name: $value") { decode(text) }
            }
        }
    }

    @Test
    fun malformedValuesFailDecoding() {
        val malformed = listOf(
            "challengeId" to "\"not-a-uuid\"",
            "userAgent" to "42",
            "userAgent" to "true",
            "userAgent" to "{}",
            "userAgent" to "[]",
            "createdAt" to "\"yesterday\"",
            "expiresAt" to "\"2026-09-24\"",
        )
        for ((field, wire) in malformed) {
            val text = tree().with(field, BackendJson.parseToJsonElement(wire))
            assertFailsWith<SerializationException>("$field: $wire") { decode(text) }
        }
        for (wire in listOf("[]", "\"preview\"", "1")) {
            assertFailsWith<SerializationException>(wire) { decode(wire) }
        }
        val duplicate = encode(preview).dropLast(1) + ",\"challengeId\":\"00000000-0000-0000-0000-000000000078\"}"
        assertEquals(Uuid.parse("00000000-0000-0000-0000-000000000078"), decode(duplicate).challengeId)
    }
}
