package dev.alllexey.itmowidgets.feature.sport.data.mapper

import dev.alllexey.itmoapi.myitmo.sport.ChosenSportLesson
import dev.alllexey.itmoapi.myitmo.sport.ChosenSportSection
import dev.alllexey.itmoapi.myitmo.sport.SportAttendance
import dev.alllexey.itmoapi.myitmo.sport.SportLessonGroup
import dev.alllexey.itmoapi.myitmo.sport.SportLesson as MyItmoSportLesson
import dev.alllexey.itmoapi.myitmo.sport.SportScore
import dev.alllexey.itmowidgets.client.sport.model.QueueEntryStatus
import dev.alllexey.itmowidgets.client.sport.model.SportFreeSignEntry
import dev.alllexey.itmowidgets.client.sport.model.SportLessonDto
import dev.alllexey.itmowidgets.feature.sport.domain.model.SectionName
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportQueueEntryStatus
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SportMappersTest {

    private val time = Instant.parse("2026-09-09T10:00:00+03:00")

    @Test
    fun `queue mapping preserves raw external and nullable online building ids`() {
        for (building in listOf<Long?>(335, 493, null)) {
            assertEquals(building, entry(lesson(building)).toModel().targetLesson.buildingId)
        }
    }

    @Test
    fun `every known queue status maps to the same domain status`() {
        for (status in QueueEntryStatus.entries - QueueEntryStatus.UNKNOWN) {
            assertEquals(SportQueueEntryStatus.valueOf(status.name), entry(lesson(1), status).toModel().status)
        }
    }

    @Test
    fun `a status this client does not know fails the mapping as 1_x did`() {
        assertThrows(IllegalStateException::class.java) { entry(lesson(1), QueueEntryStatus.UNKNOWN).toModel() }
    }

    @Test
    fun `maps a missing attendance history to an empty list`() {
        val result = SportScore(sum = SportScore.Sum(attendances = 12, other = 4), attendances = null).toModel()

        assertEquals(12, result.attendances)
        assertEquals(4, result.other)
        assertTrue(result.attendancesData.isEmpty())
    }

    @Test
    fun `blank attendance names stay absent as the 1_x nulls did`() {
        val attendance = SportAttendance(type = " lesson ", name = " ", evaluationName = "", date = time).toModel()

        assertEquals("lesson", attendance.type)
        assertNull(attendance.name)
        assertNull(attendance.evaluationName)
        assertEquals(SectionName("Плавание"), SportAttendance(name = " Плавание ", date = time).toModel().name)
    }

    @Test
    fun `a weekly series ending two days before it starts ends on its start date at the slot end`() {
        // The observed shape: Thursday 19:00 with date_end on Tuesday 21:00, both Moscow time.
        for ((start, rawEnd) in listOf("2026-10-15" to "2026-10-13", "2026-10-22" to "2026-10-20")) {
            val lesson = myItmoLesson("${start}T19:00:00+03:00", "${rawEnd}T21:00:00+03:00", slotEnd = "21:00")
            assertEquals(Instant.parse("${start}T21:00:00+03:00"), lesson.toModel(time).end)
        }
    }

    @Test
    fun `a consistent lesson end is unchanged`() {
        val lesson = myItmoLesson("2026-10-05T10:00:00+03:00", "2026-10-05T11:30:00+03:00", slotEnd = "11:45")
        assertEquals(lesson.dateEnd, lesson.toModel(time).end)
    }

    @Test
    fun `an overnight or insane end is left as sent`() {
        for (lesson in listOf(
            myItmoLesson("2026-10-15T23:00:00+03:00", "2026-10-16T01:00:00+03:00", slotEnd = "01:00"),
            myItmoLesson("2026-10-15T19:00:00+03:00", "2026-10-13T19:05:00+03:00", slotEnd = "19:05"),
            myItmoLesson("2026-10-15T08:00:00+03:00", "2026-10-13T21:00:00+03:00", slotEnd = "21:00"),
        )) {
            assertEquals(lesson.dateEnd, lesson.toModel(time).end)
        }
    }

    @Test
    fun `the lesson day is the Moscow date of its start, not the UTC date`() {
        // 00:30 Moscow on the 16th is 21:30 UTC on the 15th.
        val consistent = myItmoLesson("2026-10-15T21:30:00Z", "2026-10-15T23:00:00Z", slotEnd = "02:00")
        assertEquals(consistent.dateEnd, consistent.toModel(time).end)
        val wrongDate = myItmoLesson("2026-10-15T21:30:00Z", "2026-10-13T23:00:00Z", slotEnd = "02:00")
        assertEquals(Instant.parse("2026-10-16T02:00:00+03:00"), wrongDate.toModel(time).end)
    }

    @Test
    fun `a chosen lesson with a date_end on the wrong date ends on its start date`() {
        val start = Instant.parse("2026-10-15T19:00:00+03:00")
        val section = ChosenSportSection(
            id = 1, sectionName = "Плавание", level = 1,
            lessonGroups = listOf(
                SportLessonGroup(
                    id = 2, level = 1,
                    lessons = listOf(
                        ChosenSportLesson(id = 3, dateStart = start, dateEnd = Instant.parse("2026-10-13T21:00:00+03:00"), timeEnd = "21:00")
                    )
                )
            )
        )

        assertEquals(listOf(Instant.parse("2026-10-15T21:00:00+03:00")), section.toBookings().map { it.end })
    }

    @Test
    fun `a chosen lesson without dates is skipped instead of failing the bookings`() {
        val section = ChosenSportSection(
            id = 1, sectionName = "Плавание", level = 1,
            lessonGroups = listOf(
                SportLessonGroup(
                    id = 2, level = 1,
                    lessons = listOf(
                        ChosenSportLesson(id = 3, dateStart = time, dateEnd = time + 1.hours),
                        ChosenSportLesson(id = 4, dateStart = null, dateEnd = null)
                    )
                )
            )
        )

        assertEquals(listOf(3L), section.toBookings().map { it.lessonId })
    }

    private fun lesson(building: Long?) =
        SportLessonDto(1, 2, "Section", 1, 1, 2, building, "Room", time, time + 1.hours, 1, 900001, "Teacher")

    private fun entry(lesson: SportLessonDto, status: QueueEntryStatus = QueueEntryStatus.WAITING) = SportFreeSignEntry(
        id = 1, lessonId = 1, position = 1, total = 1, isCancelled = false, status = status,
        createdAt = time, firstNotifiedAt = null, lastNotifiedAt = null, cancelledAt = null,
        satisfiedAt = null, expiredAt = null, notificationAttempts = 0, maxNotificationAttempts = 10,
        targetLesson = lesson, forceSign = false
    )

    private fun myItmoLesson(start: String, end: String, slotEnd: String) =
        MyItmoSportLesson(date = Instant.parse(start), dateEnd = Instant.parse(end), timeSlotEnd = slotEnd)
}
