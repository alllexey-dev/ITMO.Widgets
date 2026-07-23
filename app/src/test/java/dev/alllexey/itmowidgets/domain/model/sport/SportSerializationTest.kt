package dev.alllexey.itmowidgets.domain.model.sport

import com.google.gson.GsonBuilder
import dev.alllexey.itmowidgets.core.util.OffsetDateTimeAdapter
import dev.alllexey.itmowidgets.core.utils.RuntimeTypeAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime

class SportSerializationTest {

    private val gson = GsonBuilder()
        .registerTypeAdapter(
            UnavailableReason::class.java,
            UnavailableReasonTypeAdapter()
        )
        .registerTypeAdapter(
            OffsetDateTime::class.java,
            OffsetDateTimeAdapter()
        )
        .registerTypeAdapterFactory(
            RuntimeTypeAdapterFactory
                .of(SportCommon::class.java, "type", true)
                .registerSubtype(SportBooking::class.java, "booking")
                .registerSubtype(SportLesson::class.java, "lesson")
        )
        .registerTypeAdapterFactory(
            RuntimeTypeAdapterFactory
                .of(SportQueueEntry::class.java, "type", true)
                .registerSubtype(SportFreeSignEntry::class.java, "free")
                .registerSubtype(SportAutoSignEntry::class.java, "auto")
        )
        .registerTypeAdapterFactory(
            RuntimeTypeAdapterFactory
                .of(SportQueue::class.java, "type", true)
                .registerSubtype(SportFreeSignQueue::class.java, "free")
                .registerSubtype(SportAutoSignQueue::class.java, "auto")
        )
        .create()

    @Test
    fun `round trip preserves common and queue entry subtypes`() {
        val booking = booking()

        val restored = gson.fromJson(
            gson.toJson(booking, SportCommon::class.java),
            SportCommon::class.java
        )

        assertTrue(restored is SportBooking)
        assertTrue(restored.signEntry is SportFreeSignEntry)
        assertEquals(booking, restored)
    }

    private fun booking(): SportBooking {
        val start = OffsetDateTime.parse("2026-07-22T10:00:00+03:00")
        val targetLesson = SportQueueLesson(
            id = 10,
            sectionId = 20,
            sectionName = "Плавание",
            sectionLevel = 1,
            level = 1,
            typeId = 2,
            buildingId = 30,
            roomName = "Бассейн",
            start = start,
            end = start.plusHours(1),
            timeSlotId = 40,
            teacherIsu = 50,
            teacherFio = "Иванов И. И."
        )
        val entry = SportFreeSignEntry(
            id = 1,
            lessonId = targetLesson.id,
            position = 2,
            total = 5,
            isCancelled = false,
            status = SportQueueEntryStatus.WAITING,
            createdAt = start.minusDays(1),
            firstNotifiedAt = null,
            lastNotifiedAt = null,
            cancelledAt = null,
            satisfiedAt = null,
            expiredAt = null,
            notificationAttempts = 0,
            maxNotificationAttempts = 3,
            targetLesson = targetLesson,
            forceSign = false
        )
        return SportBooking(
            isLessonReal = true,
            lessonId = targetLesson.id,
            sectionName = SectionName(targetLesson.sectionName),
            start = targetLesson.start,
            end = targetLesson.end,
            roomName = targetLesson.roomName,
            teacherFio = targetLesson.teacherFio,
            teacherIsu = targetLesson.teacherIsu.toInt(),
            sectionLevel = targetLesson.sectionLevel.toInt(),
            lessonLevel = targetLesson.level.toInt(),
            signed = false,
            signEntry = entry,
            friendsBookings = emptyList()
        )
    }
}
