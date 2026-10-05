package dev.alllexey.itmowidgets.client.sport.model

import dev.alllexey.itmowidgets.client.contract.VendoredContract
import dev.alllexey.itmowidgets.client.http.ApiEnvelope
import dev.alllexey.itmowidgets.client.json.BackendJson
import dev.alllexey.itmowidgets.client.support.assertJsonEquals
import kotlinx.serialization.KSerializer
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
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Instant

/** The sport wire models (07 CORE-K2, item 12 "Polymorphism"; 13 "More closed sets on the wire") on BK-03's data. */
class SportModelsTest {

    private val entries = ListSerializer(SportQueueEntry.serializer())

    private val lesson = SportLessonDto(
        id = 9001,
        sectionId = 41,
        sectionName = "Плавание",
        sectionLevel = 1,
        level = 1,
        typeId = 2,
        buildingId = 13,
        roomName = "Бассейн",
        start = Instant.parse("2026-10-07T07:00:00Z"),
        end = Instant.parse("2026-10-07T08:30:00Z"),
        timeSlotId = 3,
        teacherIsu = 200001,
        teacherFio = "Тренер Тестовый",
    )

    private val freeEntry = SportFreeSignEntry(
        id = 11,
        lessonId = 9001,
        position = 2,
        total = 6,
        isCancelled = false,
        status = QueueEntryStatus.WAITING,
        createdAt = Instant.parse("2026-10-04T08:00:00Z"),
        firstNotifiedAt = null,
        lastNotifiedAt = null,
        cancelledAt = null,
        satisfiedAt = null,
        expiredAt = null,
        notificationAttempts = 0,
        maxNotificationAttempts = 3,
        targetLesson = lesson,
        forceSign = false,
    )

    private fun <T> decode(serializer: KSerializer<T>, json: String): T = BackendJson.decodeFromString(serializer, json)

    private fun <T> encode(serializer: KSerializer<T>, value: T): String = BackendJson.encodeToString(serializer, value)

    private fun fixtureEntries(path: String): List<JsonObject> =
        BackendJson.parseToJsonElement(VendoredContract.read(path)).jsonObject.getValue("data").let {
            BackendJson.decodeFromJsonElement(ListSerializer(JsonObject.serializer()), it)
        }

    private val freeJson: JsonObject get() = fixtureEntries("http/sport-free-sign/mySportFreeSignEntries.json").first()

    private val autoJson: JsonObject get() = fixtureEntries("http/sport-auto-sign/mySportAutoSignEntries.json").first()

    private fun JsonObject.with(key: String, value: JsonElement) = JsonObject(this + (key to value))

    private fun JsonObject.without(key: String) = JsonObject(this - key)

    @Test
    fun freeEntryDecodesEveryField() {
        assertEquals(freeEntry, decode(SportQueueEntry.serializer(), freeJson.toString()))
    }

    @Test
    fun bothSubtypesDecodeFromOneList() {
        val decoded = SportFixtures.data("http/sport/userSportBookings.json", UserSportBookingsResponse.serializer())

        assertEquals(listOf(9001L, 9103L), decoded.lessonIds)
        assertIs<SportFreeSignEntry>(decoded.entries[0])
        val auto = assertIs<SportAutoSignEntry>(decoded.entries[1])
        assertEquals(9101, auto.prototypeLessonId)
        assertEquals(9001, auto.realLesson?.id)
    }

    @Test
    fun autoEntryWithoutRealLessonDecodesAsNull() {
        val auto = assertIs<SportAutoSignEntry>(decode(SportQueueEntry.serializer(), autoJson.toString()))

        assertNull(auto.realLessonId)
        assertNull(auto.realLesson)
        assertEquals(9101, auto.targetLesson.id)
    }

    @Test
    fun friendBookingEntryIsNullWhenAlreadySigned() {
        val bookings = SportFixtures.data(
            "http/sport/friendsSportBookings.json",
            FriendsSportBookingsResponse.serializer(),
        ).bookings

        assertNull(bookings[0].entry)
        assertIs<SportFreeSignEntry>(bookings[1].entry)
        assertIs<SportAutoSignEntry>(bookings[2].entry)
    }

    @Test
    fun unknownOrMissingTypeFails() {
        for (entry in listOf(
            freeJson.with("type", JsonPrimitive("instant")),
            freeJson.with("type", JsonPrimitive("FREE")),
            freeJson.with("type", JsonNull),
            freeJson.without("type"),
        )) {
            assertFailsWith<SerializationException>(entry.toString()) {
                decode(SportQueueEntry.serializer(), entry.toString())
            }
        }
        for (queue in listOf("""{"lessonId":1,"total":2,"type":"instant"}""", """{"lessonId":1,"total":2}""")) {
            assertFailsWith<SerializationException>(queue) { decode(SportQueue.serializer(), queue) }
        }
    }

    @Test
    fun unknownTypeInsideABookingFailsTheWholeAnswer() {
        val booking = """{"isu":100002,"lessonId":9001,"entry":${freeJson.with("type", JsonPrimitive("instant"))}}"""

        assertFailsWith<SerializationException> {
            decode(FriendsSportBookingsResponse.serializer(), """{"bookings":[$booking]}""")
        }
    }

    @Test
    fun unknownStatusDecodesAsUnknown() {
        val entry = freeJson.with("status", JsonPrimitive("TRANSFERRED"))

        assertEquals(QueueEntryStatus.UNKNOWN, decode(SportQueueEntry.serializer(), entry.toString()).status)
    }

    @Test
    fun unknownStatusIsNeverEncoded() {
        assertFailsWith<SerializationException> {
            encode(SportQueueEntry.serializer(), freeEntry.copy(status = QueueEntryStatus.UNKNOWN))
        }
    }

    @Test
    fun everyKnownStatusRoundTrips() {
        for (status in QueueEntryStatus.entries - QueueEntryStatus.UNKNOWN) {
            val entry = freeJson.with("status", JsonPrimitive(status.name))

            val decoded = decode(SportQueueEntry.serializer(), entry.toString())

            assertEquals(status, decoded.status)
            assertJsonEquals(entry.toString(), encode(SportQueueEntry.serializer(), decoded))
        }
    }

    @Test
    fun missingEntriesDecodeAsEmpty() {
        val decoded = decode(UserSportBookingsResponse.serializer(), """{"lessonIds":[9001]}""")

        assertEquals(UserSportBookingsResponse(lessonIds = listOf(9001), entries = emptyList()), decoded)
    }

    @Test
    fun missingLessonIdsFail() {
        assertFailsWith<SerializationException> {
            decode(UserSportBookingsResponse.serializer(), """{"entries":[]}""")
        }
    }

    @Test
    fun requiredEntryFieldsFailWhenMissingOrNull() {
        for (field in listOf("id", "lessonId", "isCancelled", "status", "createdAt", "targetLesson", "forceSign")) {
            for (entry in listOf(freeJson.without(field), freeJson.with(field, JsonNull))) {
                assertFailsWith<SerializationException>(entry.toString()) {
                    decode(SportQueueEntry.serializer(), entry.toString())
                }
            }
        }
    }

    @Test
    fun reEncodingThroughTheBaseSerializerKeepsType() {
        for ((path, type) in listOf(
            "http/sport-free-sign/mySportFreeSignEntries.json" to "free",
            "http/sport-auto-sign/mySportAutoSignEntries.json" to "auto",
        )) {
            for (fixture in fixtureEntries(path)) {
                val decoded = decode(SportQueueEntry.serializer(), fixture.toString())

                val encoded = encode(SportQueueEntry.serializer(), decoded)

                assertJsonEquals(fixture.toString(), encoded)
                assertEquals(JsonPrimitive(type), BackendJson.parseToJsonElement(encoded).jsonObject["type"])
            }
        }
    }

    @Test
    fun subtypeSerializerDropsType() {
        // Why consumers encode through SportQueueEntry.serializer(): the subtype serializer has no "type" member.
        val encoded = BackendJson.parseToJsonElement(encode(SportFreeSignEntry.serializer(), freeEntry)).jsonObject

        assertFalse("type" in encoded)
        assertTrue("forceSign" in encoded)
    }

    @Test
    fun queuesRoundTripWithType() {
        val queues = listOf(SportFreeSignQueue(lessonId = 9001, total = 6), SportAutoSignQueue(9101, 2, null))

        val encoded = encode(ListSerializer(SportQueue.serializer()), queues)

        assertJsonEquals(
            """[{"lessonId":9001,"total":6,"type":"free"},{"lessonId":9101,"total":2,"type":"auto"}]""",
            encoded,
        )
        assertEquals(queues, decode(ListSerializer(SportQueue.serializer()), encoded))
    }

    @Test
    fun lessonReadsTheFcmShape() {
        // Gson in FCM data drops ":00" seconds and omits a null buildingId.
        val text = """{"id":9002,"sectionId":41,"sectionName":"Плавание","sectionLevel":1,"level":1,"typeId":2,""" +
            """"roomName":"Онлайн","start":"2026-10-08T12:00+03:00","end":"2026-10-08T13:30+03:00",""" +
            """"timeSlotId":3,"teacherIsu":200001,"teacherFio":"Тренер Тестовый"}"""

        val decoded = decode(SportLessonDto.serializer(), text)

        assertNull(decoded.buildingId)
        assertEquals(Instant.parse("2026-10-08T09:00:00Z"), decoded.start)
        assertEquals(Instant.parse("2026-10-08T10:30:00Z"), decoded.end)
    }

    @Test
    fun limitsDecode() {
        assertEquals(
            SportAutoSignLimits(limit = 3, available = 0, nextAvailableAt = Instant.parse("2026-10-25T08:10:00Z")),
            SportFixtures.data("http/sport-auto-sign/sportAutoSignLimits.json", SportAutoSignLimits.serializer()),
        )
    }

    @Test
    fun requestBodiesEqualTheVendoredRequests() {
        assertJsonEquals(
            VendoredContract.read("requests/SportFreeSignRequest.json"),
            encode(SportFreeSignRequest.serializer(), SportFreeSignRequest(lessonId = 9001, forceSign = true)),
        )
        assertJsonEquals(
            VendoredContract.read("requests/SportAutoSignRequest.json"),
            encode(SportAutoSignRequest.serializer(), SportAutoSignRequest(prototypeLessonId = 9101)),
        )
    }
}

/** Decoded `data` of the vendored sport answers. */
internal object SportFixtures {
    fun <T> data(path: String, serializer: KSerializer<T>): T =
        BackendJson.decodeFromString(ApiEnvelope.serializer(serializer), VendoredContract.read(path)).data
            ?: fail("$path has no data")
}
