package dev.alllexey.itmowidgets.client.pairing

import dev.alllexey.itmowidgets.client.json.BackendJson
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
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

/** The pairing models: exact names, nullable fields, required fields, and the display enums. */
class PairingContractTest {

    private val preview = PairingPreview(
        challengeId = Uuid.parse("00000000-0000-4000-8000-000000000501"),
        kind = PairingKind.AGENT,
        name = "nas",
        version = "0.1.0",
        userAgent = null,
        keyCommitment = "AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE",
        scopes = null,
        createdAt = Instant.parse("2026-10-05T09:00:00Z"),
        expiresAt = Instant.parse("2026-10-05T09:10:00Z"),
    )

    private val connection = ConnectionInfo(
        id = Uuid.parse("00000000-0000-4000-8000-000000000601"),
        kind = ConnectionKind.AGENT,
        name = "nas",
        userAgent = null,
        createdAt = Instant.parse("2026-10-05T09:00:00Z"),
        lastUsedAt = null,
        live = true,
        itmoSession = true,
        current = false,
        scopes = listOf(AgentScope.SPORT, AgentScope.SCHEDULE_WRITE),
    )

    private fun tree(value: PairingPreview): JsonObject =
        BackendJson.parseToJsonElement(BackendJson.encodeToString(PairingPreview.serializer(), value)).jsonObject

    private fun decodePreview(text: String) = BackendJson.decodeFromString(PairingPreview.serializer(), text)

    private fun decodeConnection(text: String) = BackendJson.decodeFromString(ConnectionInfo.serializer(), text)

    private fun JsonObject.with(name: String, value: JsonElement?): String =
        JsonObject(if (value == null) this - name else this + (name to value)).toString()

    @Test
    fun modelsRoundTrip() {
        assertEquals(preview, decodePreview(BackendJson.encodeToString(PairingPreview.serializer(), preview)))
        assertEquals(
            connection,
            decodeConnection(BackendJson.encodeToString(ConnectionInfo.serializer(), connection)),
        )
    }

    @Test
    fun nullableFieldsDecodeFromNullAndAbsence() {
        val full = tree(preview.copy(scopes = listOf(AgentScope.SPORT), userAgent = "UA"))
        val fields = mapOf<String, (PairingPreview) -> Any?>(
            "name" to { it.name },
            "version" to { it.version },
            "userAgent" to { it.userAgent },
            "keyCommitment" to { it.keyCommitment },
            "scopes" to { it.scopes },
        )
        for ((name, field) in fields) {
            for (value in listOf(JsonNull, null)) {
                assertEquals(null, field(decodePreview(full.with(name, value))), name)
            }
        }
    }

    @Test
    fun requiredFieldsNeverBecomeDefaults() {
        for (name in listOf("challengeId", "kind", "createdAt", "expiresAt")) {
            for (value in listOf(null, JsonNull, JsonPrimitive(1))) {
                val text = tree(preview).with(name, value)
                assertFailsWith<SerializationException>("$name: $value") { decodePreview(text) }
            }
        }
    }

    @Test
    fun scopesUseTheirWireNamesAndANewOneIsUnknown() {
        val wire = """["sport","friends:read","schedule:write","calendar:write"]"""
        val decoded = BackendJson.decodeFromString(ListSerializer(AgentScope.serializer()), wire)

        assertEquals(
            listOf(AgentScope.SPORT, AgentScope.FRIENDS_READ, AgentScope.SCHEDULE_WRITE, AgentScope.UNKNOWN),
            decoded,
        )
        // Constant names are not wire values.
        assertEquals(AgentScope.UNKNOWN, BackendJson.decodeFromString(AgentScope.serializer(), "\"FRIENDS_READ\""))
        val unknown = ApprovePairingRequest(null, listOf(AgentScope.UNKNOWN))
        assertFailsWith<SerializationException> { BackendJson.encodeToString(ApprovePairingRequest.serializer(), unknown) }
    }

    @Test
    fun aNewKindDecodesAsUnknownAndIsNeverSent() {
        assertEquals(PairingKind.UNKNOWN, decodePreview(tree(preview).with("kind", JsonPrimitive("TV"))).kind)
        val text = BackendJson.encodeToString(ConnectionInfo.serializer(), connection)
        val unknown = BackendJson.parseToJsonElement(text).jsonObject.with("kind", JsonPrimitive("WEB_ITMO"))
        assertEquals(ConnectionKind.UNKNOWN, decodeConnection(unknown).kind)
        assertFailsWith<SerializationException> {
            BackendJson.encodeToString(PairingKind.serializer(), PairingKind.UNKNOWN)
        }
    }
}
