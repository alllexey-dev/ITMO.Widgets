package dev.alllexey.itmowidgets.client.sport

import dev.alllexey.itmowidgets.client.json.BackendJson
import dev.alllexey.itmowidgets.client.sport.model.FriendSportBooking
import dev.alllexey.itmowidgets.client.sport.model.FriendsSportBookingsResponse
import dev.alllexey.itmowidgets.client.sport.model.QueueEntryStatus
import dev.alllexey.itmowidgets.client.sport.model.SportAutoSignEntry
import dev.alllexey.itmowidgets.client.sport.model.SportFreeSignEntry
import dev.alllexey.itmowidgets.client.sport.model.SportLessonDto
import dev.alllexey.itmowidgets.client.sport.model.SportQueueEntry
import dev.alllexey.itmowidgets.client.sport.model.UserSportBookingsResponse
import dev.alllexey.itmowidgets.client.support.MockBackend
import dev.alllexey.itmowidgets.client.support.ok
import dev.alllexey.itmowidgets.client.support.record
import dev.alllexey.itmowidgets.client.support.runSuspend
import dev.alllexey.itmowidgets.client.users.SyntheticUsers
import io.ktor.http.HttpMethod
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Port of Core 1.7.0 `SportApiContractTest` (5 tests). The Retrofit reflection check becomes an assertion over the
 * recorded request, Gson round trips go through `BackendJson` and the base serializer, and `JsonParseException`
 * becomes a `SerializationException`.
 */
class SportApiContractTest {

    private val timestamp = Instant.parse("2026-09-07T09:00:00Z")
    private val lesson = SportLessonDto(
        id = 100,
        sectionId = 200,
        sectionName = "Плавание",
        sectionLevel = 1,
        level = 1,
        typeId = 2,
        buildingId = 300,
        roomName = "Бассейн",
        start = timestamp,
        end = timestamp + 90.minutes,
        timeSlotId = 400,
        teacherIsu = 200001,
        teacherFio = "Тренер Тестовый",
    )

    private val freeEntry = SportFreeSignEntry(
        id = 1,
        lessonId = lesson.id,
        position = 1,
        total = 2,
        isCancelled = false,
        status = QueueEntryStatus.WAITING,
        createdAt = timestamp,
        firstNotifiedAt = null,
        lastNotifiedAt = null,
        cancelledAt = null,
        satisfiedAt = null,
        expiredAt = null,
        notificationAttempts = 0,
        maxNotificationAttempts = 10,
        targetLesson = lesson,
        forceSign = false,
    )

    private val autoEntry = SportAutoSignEntry(
        id = 2,
        prototypeLessonId = lesson.id,
        realLessonId = null,
        position = 2,
        total = 3,
        isCancelled = false,
        status = QueueEntryStatus.NOTIFIED,
        createdAt = timestamp,
        firstNotifiedAt = timestamp,
        lastNotifiedAt = timestamp,
        cancelledAt = null,
        satisfiedAt = null,
        expiredAt = null,
        notificationAttempts = 1,
        maxNotificationAttempts = 10,
        targetLesson = lesson,
        realLesson = null,
    )

    @Test
    fun friendsSportBookingsUseTheExpectedGetRoute() = runSuspend {
        val request = SportRouteCases.friendsSportBookings.record()

        assertEquals(HttpMethod.Get, request.method)
        assertEquals("/api/sport/friends/sport-bookings", request.path)
    }

    @Test
    fun friendBookingQueueEntrySubtypesRoundTrip() {
        val source = FriendsSportBookingsResponse(
            bookings = listOf(
                FriendSportBooking(isu = SyntheticUsers.ISU, lessonId = lesson.id, entry = freeEntry),
                FriendSportBooking(isu = SyntheticUsers.OTHER_ISU, lessonId = lesson.id, entry = autoEntry),
            ),
        )
        val serializer = FriendsSportBookingsResponse.serializer()

        val restored = BackendJson.decodeFromString(serializer, BackendJson.encodeToString(serializer, source))

        assertIs<SportFreeSignEntry>(restored.bookings[0].entry)
        assertIs<SportAutoSignEntry>(restored.bookings[1].entry)
        assertEquals(source, restored)
    }

    @Test
    fun rawExternalAndNullableOnlineVenuesRoundTripInBothQueueTypes() {
        for (building in listOf<Long?>(335L, 493L, null, -1L)) {
            val online = building == null || building == -1L
            val venue = lesson.copy(buildingId = building, roomName = if (online) "Онлайн" else "Внешняя площадка")
            val entries = listOf(
                freeEntry.copy(targetLesson = venue),
                autoEntry.copy(targetLesson = venue, realLesson = venue),
            )
            for (source in entries) {
                val json = BackendJson.encodeToJsonElement(SportQueueEntry.serializer(), source).jsonObject
                // Jackson writes an explicit null; the client omits it. Both must read the same.
                val buildingId = building?.let(::JsonPrimitive) ?: JsonNull
                val target = JsonObject(json.getValue("targetLesson").jsonObject + ("buildingId" to buildingId))
                val explicit = JsonObject(json + ("targetLesson" to target))

                val restored = BackendJson.decodeFromJsonElement(SportQueueEntry.serializer(), explicit)

                assertEquals(source, restored)
                assertEquals(building, restored.targetLesson.buildingId)
            }
        }
    }

    @Test
    fun targetSportResponseDecodesConfirmedIdsAndBothActiveQueueTypesThroughTheClient() = runSuspend {
        val source = UserSportBookingsResponse(listOf(2_147_483_648L), listOf(freeEntry, autoEntry))
        val data = BackendJson.encodeToString(UserSportBookingsResponse.serializer(), source)
        val backend = MockBackend { ok(SyntheticUsers.envelope(data)) }

        val result = backend.client.sport.userSportBookings(SyntheticUsers.ISU)

        assertEquals(source, result)
        assertIs<SportFreeSignEntry>(result.entries[0])
        assertIs<SportAutoSignEntry>(result.entries[1])
        assertEquals(HttpMethod.Get, backend.lastRequest.method)
        assertEquals("/api/sport/users/123456/bookings", backend.lastRequest.url.encodedPath)
    }

    @Test
    fun confirmedOnlyServerDefaultsMissingEntriesAndMalformedArraysAreRejected() {
        val serializer = UserSportBookingsResponse.serializer()

        assertEquals(
            UserSportBookingsResponse(listOf(100L)),
            BackendJson.decodeFromString(serializer, """{"lessonIds":[100]}"""),
        )
        for (json in listOf(
            "{}",
            """{"lessonIds":null}""",
            """{"lessonIds":[],"entries":null}""",
            """{"lessonIds":[],"entries":{}}""",
            """{"lessonIds":[],"entries":[{"type":"unknown"}]}""",
        )) {
            assertFailsWith<SerializationException>(json) { BackendJson.decodeFromString(serializer, json) }
        }
    }
}
