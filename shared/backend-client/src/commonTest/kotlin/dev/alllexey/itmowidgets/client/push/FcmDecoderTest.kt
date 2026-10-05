package dev.alllexey.itmowidgets.client.push

import dev.alllexey.itmowidgets.client.error.BackendException
import dev.alllexey.itmowidgets.client.json.BackendJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.time.Instant

/** The standalone FCM decoder: envelope shape, unknown types, both date writers and failures that stay closed. */
class FcmDecoderTest {

    @Test
    fun unknownTypeIsReturnedNotThrown() {
        val envelope = FcmDecoder.envelope("""{"type":"FUTURE_PAYLOAD","payload":{"anything":[1,2]}}""")

        assertEquals("FUTURE_PAYLOAD", envelope.type)
        assertEquals(BackendJson.parseToJsonElement("""{"anything":[1,2]}"""), envelope.payload)
    }

    @Test
    fun envelopeIgnoresUnknownKeys() {
        val envelope = FcmDecoder.envelope("""{"type":"FUTURE_PAYLOAD","payload":{},"version":2}""")

        assertEquals(FcmEnvelope("FUTURE_PAYLOAD", JsonObject(emptyMap())), envelope)
    }

    @Test
    fun malformedEnvelopeFails() {
        val malformed = listOf(
            "",
            "not json",
            "null",
            "[]",
            "{}",
            """{"payload":{}}""",
            """{"type":null,"payload":{}}""",
            """{"type":42,"payload":{}}""",
            """{"type":"FRIENDSHIP_EVENT_PAYLOAD"}""",
            """{"type":"FRIENDSHIP_EVENT_PAYLOAD","payload":null}""",
            """{"type":"FRIENDSHIP_EVENT_PAYLOAD","payload":[]}""",
            """{"type":"FRIENDSHIP_EVENT_PAYLOAD","payload":"{}"}""",
        )
        for (data in malformed) assertFailsWith<BackendException.Contract>(data) { FcmDecoder.envelope(data) }
    }

    @Test
    fun failureMessageNeverQuotesThePayload() {
        val data = SyntheticFcm.GSON_FRIENDSHIP_EVENT.replace("REQUEST_RECEIVED", "SECRET_EVENT")

        val error = assertFailsWith<BackendException.Contract> {
            FcmDecoder.friendshipEvent(FcmDecoder.envelope(data).payload)
        }

        assertFalse(error.message.orEmpty().contains("SECRET_EVENT"))
        assertFalse(error.message.orEmpty().contains("100002"))
    }

    @Test
    fun gsonAndJacksonDatesDecodeToTheSameInstant() {
        val gson = FcmDecoder.envelope(SyntheticFcm.GSON_FRIENDSHIP_EVENT)
        val jackson = FcmDecoder.envelope(SyntheticFcm.jacksonDates(SyntheticFcm.GSON_FRIENDSHIP_EVENT))

        val fromGson = FcmDecoder.friendshipEvent(gson.payload)

        assertEquals(Instant.parse("2026-10-05T09:00:00Z"), fromGson.occurredAt)
        assertEquals(fromGson, FcmDecoder.friendshipEvent(jackson.payload))
    }

    @Test
    fun sportPayloadsKeepAnOmittedBuildingAsNull() {
        val payload = FcmDecoder.envelope(SyntheticFcm.GSON_SPORT_AUTO_SIGN_LESSONS).payload

        val lessons = FcmDecoder.sportAutoSignLessons(payload).sportLessons

        assertEquals(listOf(9001L, 9002L), lessons.map { it.id })
        assertEquals(13L, lessons[0].buildingId)
        assertNull(lessons[1].buildingId)
        assertEquals(Instant.parse("2026-10-07T07:00:00Z"), lessons[0].start)
        assertEquals(lessons, FcmDecoder.sportFreeSignLessons(payload).sportLessons)
    }

    @Test
    fun sportPayloadWithoutLessonsOrWithAnIncompleteLessonFails() {
        val payload = FcmDecoder.envelope(SyntheticFcm.GSON_SPORT_AUTO_SIGN_LESSONS).payload
        val lesson = payload.getValue("sportLessons").jsonArray[0].jsonObject
        val incomplete = JsonObject(mapOf("sportLessons" to JsonArray(listOf(JsonObject(lesson - "start")))))

        for (bad in listOf(JsonObject(emptyMap()), incomplete, JsonPrimitive(1))) {
            assertFailsWith<BackendException.Contract>(bad.toString()) { FcmDecoder.sportFreeSignLessons(bad) }
            assertFailsWith<BackendException.Contract>(bad.toString()) { FcmDecoder.sportAutoSignLessons(bad) }
        }
    }
}
