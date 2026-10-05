package dev.alllexey.itmowidgets.client.json

import dev.alllexey.itmowidgets.client.error.BackendException
import dev.alllexey.itmowidgets.client.http.BackendRoute
import dev.alllexey.itmowidgets.client.http.jsonBody
import dev.alllexey.itmowidgets.client.support.MockBackend
import dev.alllexey.itmowidgets.client.support.runSuspend
import io.ktor.http.HttpMethod
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The three enum policies (13 Q7 (b), SP-02 cases 9 and 16) on synthetic stand-ins: the area cards apply them to
 * the real enums (`RestrictionCapability` in links, `QueueEntryStatus` in sport, ...).
 */
class EnumPolicyTest {

    private val nonStrings = listOf("null", "1", "true", "{}", "[]")

    private fun <E> decode(serializer: KSerializer<E>, text: String): E = BackendJson.decodeFromString(serializer, text)

    private fun <E> encode(serializer: KSerializer<E>, value: E): String = BackendJson.encodeToString(serializer, value)

    @Test
    fun strictRoundTripsKnownValuesAndRejectsTheRest() {
        for (value in Visibility.entries) {
            assertEquals(value, decode(VisibilitySerializer, "\"${value.name}\""))
            assertEquals("\"${value.name}\"", encode(VisibilitySerializer, value))
        }
        for (text in listOf("\"CLASSMATES\"", "\"friends\"", "\"\"") + nonStrings) {
            assertFailsWith<SerializationException>(text) { decode(VisibilitySerializer, text) }
        }
    }

    @Test
    fun strictUnknownFailsTheEnclosingModel() {
        assertFailsWith<SerializationException> {
            BackendJson.decodeFromString(Settings.serializer(), """{"visibility":"CLASSMATES"}""")
        }
    }

    @Test
    fun fallbackDecodesUnknownAsTheMostRestrictiveValue() {
        // SP-02 case 9: a capability added by a newer server restricts everything on this client.
        assertEquals(Capability.ALL, decode(CapabilitySerializer, "\"UPLOAD_FILES\""))
        for (value in Capability.entries) {
            assertEquals(value, decode(CapabilitySerializer, "\"${value.name}\""))
            assertEquals("\"${value.name}\"", encode(CapabilitySerializer, value))
        }
        for (text in nonStrings) {
            assertFailsWith<SerializationException>(text) { decode(CapabilitySerializer, text) }
        }
    }

    @Test
    fun tolerantDecodesUnknownAsUnknown() {
        // SP-02 case 16: 1.x put a null into the non-null QueueEntryStatus field instead.
        assertEquals(Status.UNKNOWN, decode(StatusSerializer, "\"PAUSED\""))
        for (value in Status.entries - Status.UNKNOWN) {
            assertEquals(value, decode(StatusSerializer, "\"${value.name}\""))
            assertEquals("\"${value.name}\"", encode(StatusSerializer, value))
        }
        for (text in nonStrings) {
            assertFailsWith<SerializationException>(text) { decode(StatusSerializer, text) }
        }
    }

    @Test
    fun tolerantNeverEncodesUnknown() {
        assertFailsWith<SerializationException> { encode(StatusSerializer, Status.UNKNOWN) }
    }

    @Test
    fun unknownInARequestBodyIsAContractFailure() = runSuspend {
        val backend = MockBackend()

        val failure = assertFailsWith<BackendException.Contract> {
            backend.http.callUnit(
                BackendRoute(HttpMethod.Put, listOf("api", "probe"), body = jsonBody(Entry(Status.UNKNOWN))),
            )
        }

        assertTrue(failure.cause is SerializationException)
        assertTrue(backend.requests.isEmpty())
    }

    @Serializable(with = VisibilitySerializer::class)
    enum class Visibility { ALL, FRIENDS, NOBODY }

    internal object VisibilitySerializer : StrictEnumSerializer<Visibility>("Visibility", Visibility.entries)

    @Serializable
    class Settings(val visibility: Visibility)

    @Serializable(with = CapabilitySerializer::class)
    enum class Capability { VOTE, REPORT, ALL }

    internal object CapabilitySerializer :
        FallbackEnumSerializer<Capability>("Capability", Capability.entries, Capability.ALL)

    @Serializable(with = StatusSerializer::class)
    enum class Status { WAITING, SATISFIED, UNKNOWN }

    internal object StatusSerializer :
        UnknownTolerantEnumSerializer<Status>("Status", Status.entries, Status.UNKNOWN)

    @Serializable
    class Entry(val status: Status)
}
