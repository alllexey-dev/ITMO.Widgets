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
import kotlin.test.assertNull
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
    fun aProtocol2PreviewCarriesTheAppAndTheGrantsInOrder() {
        val wire = tree(preview) + mapOf(
            "client" to JsonPrimitive("mcp"),
            "protocol" to JsonPrimitive(2),
            "app" to BackendJson.parseToJsonElement(
                """{"id":"itmo-mcp","name":"ITMO MCP","homepage":null,"iconUrl":null,"deployment":"self_hosted",
                    "verified":false}""",
            ),
            "grants" to BackendJson.parseToJsonElement(
                """[{"id":"widgets:reviews:read","required":false,"title":"Отзывы","description":"Читает отзывы",
                     "purpose":null,"granted":null},
                    {"id":"itmo:refresh:student-personal-cabinet","required":false,"title":null,"description":null,
                     "purpose":"Расписание, зачётка, физкультура и заявки в my.itmo","granted":null},
                    {"id":"itmo:sso","required":false,"title":null,"description":null,
                     "purpose":"Баллы БАРС и вход в другие сервисы ИТМО без повторной привязки","granted":null}]""",
            ),
        )

        val decoded = decode(JsonObject(wire).toString())

        assertEquals(2, decoded.protocol)
        assertEquals(ClientAppInfo("itmo-mcp", "ITMO MCP", null, null, ClientDeployment.SELF_HOSTED, false), decoded.app)
        assertEquals(
            listOf("widgets:reviews:read", "itmo:refresh:student-personal-cabinet", "itmo:sso"),
            decoded.grants?.map { it.id },
        )
        assertEquals("Отзывы", decoded.grants?.first()?.title)
        assertNull(decoded.grants?.last()?.title)
        assertEquals(decoded, decode(BackendJson.encodeToString(PairingPreview.serializer(), decoded)))
    }

    @Test
    fun aPreviewOfAnOlderBackendHasNoProtocol2Fields() {
        val decoded = decode(tree(preview).toString())

        assertNull(decoded.protocol)
        assertNull(decoded.app)
        assertNull(decoded.grants)
    }

    @Test
    fun aRenewalSaysWhichItmoGrantsTheConnectionHolds() {
        val grants = BackendJson.decodeFromString(
            ListSerializer(GrantRequest.serializer()),
            """[{"id":"itmo:refresh:student-personal-cabinet","required":true,"granted":true},
                {"id":"itmo:sso","required":false,"granted":false}]""",
        )

        assertEquals(listOf(true, false), grants.map { it.granted })
        assertEquals(listOf(true, false), grants.map { it.required })
    }

    @Test
    fun aNewDeploymentIsUnknownAndAGrantWithoutRequiredFails() {
        val app = BackendJson.decodeFromString(
            ClientAppInfo.serializer(),
            """{"id":"x","name":"X","deployment":"edge","verified":false}""",
        )

        assertEquals(ClientDeployment.UNKNOWN, app.deployment)
        assertFailsWith<SerializationException> {
            BackendJson.decodeFromString(GrantRequest.serializer(), """{"id":"itmo:sso"}""")
        }
    }

    @Test
    fun aConnectionNamesItsRegistryEntryAndItsItmoGrants() {
        val connection = BackendJson.decodeFromString(
            ConnectionInfo.serializer(),
            """{"id":"00000000-0000-4000-8000-000000000601","client":"mcp","name":"MacBook Air","version":"0.4",
                "platform":"macos","scopes":[],"createdAt":"2026-10-01T09:00:00Z","approvedAt":"2026-10-01T09:00:00Z",
                "lastUsedAt":null,"live":true,"itmo":{"sid":"sid-1","expiresAt":"2026-12-30T09:00:00Z"},
                "itmoStatus":"ok","revokedAt":null,"clientId":"itmo-mcp",
                "app":{"id":"itmo-mcp","name":"ITMO MCP","homepage":null,"iconUrl":null,"deployment":"self_hosted",
                       "verified":false},
                "itmoGrants":["itmo:refresh:student-personal-cabinet","itmo:sso"]}""",
        )

        assertEquals("itmo-mcp", connection.clientId)
        assertEquals("ITMO MCP", connection.app?.name)
        assertEquals(listOf("itmo:refresh:student-personal-cabinet", "itmo:sso"), connection.itmoGrants)
    }

    @Test
    fun aSessionNeverPrintsItsSid() {
        val session = ItmoSession("secret-sid", Instant.parse("2026-12-30T09:00:00Z"))

        assertFalse("secret-sid" in session.toString())
    }
}
