package dev.alllexey.itmowidgets.client.connections

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
import kotlin.test.assertFalse
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** The connections models: required fields, the display enums with wire names, and no `sid` in `toString`. */
class ConnectionsContractTest {

    private val preview = PairingPreview(
        challengeId = Uuid.parse("00000000-0000-4000-8000-000000000501"),
        kind = PairingKind.CONNECT,
        client = ConnectionClient.AGENT,
        name = "nas",
        version = "1.2.0",
        platform = "linux",
        requestedScopes = listOf(ScopeInfo(ConnectionScope.SPORT, "Запись на физкультуру", "Записывается")),
        keyCommitment = "AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE",
        connectionId = null,
        expiresAt = Instant.parse("2026-10-05T09:10:00Z"),
    )

    private fun tree(value: PairingPreview): JsonObject =
        BackendJson.parseToJsonElement(BackendJson.encodeToString(PairingPreview.serializer(), value)).jsonObject

    private fun decode(text: String) = BackendJson.decodeFromString(PairingPreview.serializer(), text)

    private fun JsonObject.with(name: String, value: JsonElement?): String =
        JsonObject(if (value == null) this - name else this + (name to value)).toString()

    @Test
    fun previewRoundTrips() {
        assertEquals(preview, decode(BackendJson.encodeToString(PairingPreview.serializer(), preview)))
    }

    @Test
    fun requiredFieldsNeverBecomeDefaults() {
        for (name in listOf("challengeId", "kind", "client", "name", "requestedScopes", "keyCommitment", "expiresAt")) {
            for (value in listOf(null, JsonNull)) {
                assertFailsWith<SerializationException>("$name: $value") { decode(tree(preview).with(name, value)) }
            }
        }
    }

    @Test
    fun scopesUseTheirWireNamesAndANewOneIsUnknown() {
        val wire = """["sport","friends:read","schedule:write","reviews:read","links:read","sport:queue","user","x"]"""
        val decoded = BackendJson.decodeFromString(ListSerializer(ConnectionScope.serializer()), wire)

        assertEquals(ConnectionScope.entries, decoded)
        assertEquals(ConnectionScope.UNKNOWN, BackendJson.decodeFromString(ConnectionScope.serializer(), "\"SPORT\""))
        assertFailsWith<SerializationException> {
            BackendJson.encodeToString(UpdateScopesRequest.serializer(), UpdateScopesRequest(listOf(ConnectionScope.UNKNOWN)))
        }
    }

    @Test
    fun newKindsAndClientsDecodeAsUnknownAndAreNeverSent() {
        val wire = tree(preview) + ("kind" to JsonPrimitive("MOVE")) + ("client" to JsonPrimitive("tv"))
        val unknown = decode(JsonObject(wire).toString())

        assertEquals(PairingKind.UNKNOWN, unknown.kind)
        assertEquals(ConnectionClient.UNKNOWN, unknown.client)
        assertEquals(ItmoStatus.UNKNOWN, BackendJson.decodeFromString(ItmoStatus.serializer(), "\"lost\""))
        assertFailsWith<SerializationException> {
            BackendJson.encodeToString(PairingKind.serializer(), PairingKind.UNKNOWN)
        }
    }

    @Test
    fun aSessionNeverPrintsItsSid() {
        val session = ItmoSession("secret-sid", Instant.parse("2026-12-30T09:00:00Z"))

        assertFalse("secret-sid" in session.toString())
    }
}
