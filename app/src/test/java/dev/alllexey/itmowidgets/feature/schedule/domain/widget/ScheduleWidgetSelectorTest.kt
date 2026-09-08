package dev.alllexey.itmowidgets.feature.schedule.domain.widget

import dev.alllexey.itmowidgets.core.settings.LessonStyle
import dev.alllexey.itmowidgets.core.sport.PendingSportBooking
import dev.alllexey.itmowidgets.feature.schedule.domain.model.DaySchedule
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Lesson
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleWidgetSelectorTest {

    private val selector = ScheduleWidgetSelector()

    @Test
    fun `shows current lesson and schedules forward switch before its end`() {
        val first = lesson(1, "09:30", "11:00", "Математика")
        val second = lesson(2, "11:20", "12:50", "Физика")

        val result = selector.select(
            schedule = listOf(day(TODAY, first, second)),
            now = at("10:00"),
            preferences = preferences(forwardScheduling = true)
        )

        assertEquals("Математика", result.snapshot.singleLesson.lesson?.subject)
        assertEquals(
            ScheduleWidgetLessonState.CURRENT,
            result.snapshot.singleLesson.lesson?.state
        )
        assertEquals(1, result.snapshot.singleLesson.remainingLessons)
        assertEquals(Duration.ofMinutes(45), result.nextUpdateDelay)
    }

    @Test
    fun `moves to next lesson during forward window`() {
        val first = lesson(1, "09:30", "11:00", "Математика")
        val second = lesson(2, "11:20", "12:50", "Физика")

        val result = selector.select(
            schedule = listOf(day(TODAY, first, second)),
            now = at("10:50"),
            preferences = preferences(forwardScheduling = true)
        )

        assertEquals("Физика", result.snapshot.singleLesson.lesson?.subject)
        assertEquals(
            ScheduleWidgetLessonState.UPCOMING,
            result.snapshot.singleLesson.lesson?.state
        )
        assertEquals(0, result.snapshot.singleLesson.remainingLessons)
        assertEquals(Duration.ofHours(2), result.nextUpdateDelay)
    }

    @Test
    fun `shows tomorrow in list after todays lessons finish`() {
        val todayLesson = lesson(1, "08:20", "09:50", "История")
        val tomorrowLesson = lesson(2, "10:00", "11:30", "Алгоритмы")

        val result = selector.select(
            schedule = listOf(
                day(TODAY, todayLesson),
                day(TODAY.plusDays(1), tomorrowLesson)
            ),
            now = at("18:00"),
            preferences = preferences(showTomorrowWhenFinished = true)
        )

        assertEquals(
            SingleLessonWidgetKind.NO_MORE_TODAY,
            result.snapshot.singleLesson.kind
        )
        assertEquals(
            listOf(
                ScheduleListWidgetItemKind.HEADER,
                ScheduleListWidgetItemKind.LESSON,
                ScheduleListWidgetItemKind.END
            ),
            result.snapshot.lessonList.map(ScheduleListWidgetItem::kind)
        )
        assertEquals(true, result.snapshot.lessonList.first().tomorrow)
        assertEquals("Алгоритмы", result.snapshot.lessonList[1].lesson?.subject)
    }

    @Test
    fun `hides completed lessons from day list`() {
        val completed = lesson(1, "08:20", "09:50", "История")
        val current = lesson(2, "10:00", "11:30", "Алгоритмы")
        val upcoming = lesson(3, "11:40", "13:10", "Физика")

        val result = selector.select(
            schedule = listOf(day(TODAY, completed, current, upcoming)),
            now = at("10:30"),
            preferences = preferences(hidePreviousLessons = true)
        )

        assertEquals(
            listOf("Алгоритмы", "Физика"),
            result.snapshot.lessonList.mapNotNull { it.lesson?.subject }
        )
    }

    @Test
    fun `reports empty today and tomorrow without exposing teacher`() {
        val result = selector.select(
            schedule = emptyList(),
            now = at("10:30"),
            preferences = preferences(
                hideTeacher = true,
                showTomorrowWhenFinished = true
            )
        )

        assertEquals(
            SingleLessonWidgetKind.EMPTY_TODAY,
            result.snapshot.singleLesson.kind
        )
        assertEquals(
            ScheduleListWidgetItemKind.EMPTY_TODAY_AND_TOMORROW,
            result.snapshot.lessonList.single().kind
        )
        assertNull(result.snapshot.singleLesson.lesson)
    }

    @Test
    fun `uses seven minute refresh when smart scheduling is disabled`() {
        val result = selector.select(
            schedule = listOf(day(TODAY, lesson(1, "08:20", "19:50", "Практика"))),
            now = at("10:30"),
            preferences = preferences(smartScheduling = false)
        )

        assertEquals(Duration.ofMinutes(7), result.nextUpdateDelay)
    }

    @Test
    fun `removes teacher according to widget privacy setting`() {
        val result = selector.select(
            schedule = listOf(
                day(
                    TODAY,
                    lesson(
                        id = 1,
                        start = "10:00",
                        end = "11:30",
                        subject = "Алгоритмы",
                        teacher = "Иванов Иван Иванович"
                    )
                )
            ),
            now = at("10:30"),
            preferences = preferences(hideTeacher = true)
        )

        assertNull(result.snapshot.singleLesson.lesson?.teacher)
        assertNull(result.snapshot.lessonList.first().lesson?.teacher)
    }

    @Test
    fun `pending only today appears in both widgets with waiting and predicted markers`() {
        val result = selector.select(
            schedule = emptyList(),
            now = at("10:00"),
            preferences = preferences(),
            pendingSport = listOf(
                pending(2, "13:00", prediction = true),
                pending(1, "11:00")
            )
        )

        assertEquals(SingleLessonWidgetKind.LESSON, result.snapshot.singleLesson.kind)
        assertEquals("Секция 1", result.snapshot.singleLesson.lesson?.subject)
        assertEquals(ScheduleWidgetPendingStatus.WAITING, result.snapshot.singleLesson.lesson?.pendingStatus)
        assertEquals(ScheduleWidgetLessonState.UPCOMING, result.snapshot.singleLesson.lesson?.state)
        assertEquals(1, result.snapshot.singleLesson.remainingLessons)
        val displayed = result.snapshot.lessonList.mapNotNull { it.lesson }
        assertEquals(listOf("Секция 1", "Секция 2"), displayed.map { it.subject })
        assertEquals(
            listOf(ScheduleWidgetPendingStatus.WAITING, ScheduleWidgetPendingStatus.PREDICTED),
            displayed.map { it.pendingStatus }
        )
        assertTrue(displayed.all { it.typeId == 11 && it.state == ScheduleWidgetLessonState.UPCOMING })
        assertEquals(ScheduleListWidgetItemKind.END, result.snapshot.lessonList.last().kind)
    }

    @Test
    fun `pending only tomorrow appears in tomorrow list without becoming todays next lesson`() {
        val tomorrow = pending(1, "11:00", prediction = true, date = TODAY.plusDays(1))
        val result = selector.select(
            schedule = emptyList(),
            now = at("18:00"),
            preferences = preferences(showTomorrowWhenFinished = true),
            pendingSport = listOf(tomorrow)
        )

        assertEquals(SingleLessonWidgetKind.EMPTY_TODAY, result.snapshot.singleLesson.kind)
        assertNull(result.snapshot.singleLesson.lesson)
        assertEquals(
            listOf(ScheduleListWidgetItemKind.HEADER, ScheduleListWidgetItemKind.LESSON, ScheduleListWidgetItemKind.END),
            result.snapshot.lessonList.map { it.kind }
        )
        assertEquals(TODAY.plusDays(1).toString(), result.snapshot.lessonList.first().dateIso)
        assertTrue(result.snapshot.lessonList.first().tomorrow)
        assertTrue(result.snapshot.lessonList.last().tomorrow)
        assertEquals(ScheduleWidgetPendingStatus.PREDICTED, result.snapshot.lessonList[1].lesson?.pendingStatus)

        val todayOnly = selector.select(emptyList(), at("18:00"), preferences(), listOf(tomorrow))
        assertEquals(ScheduleListWidgetItemKind.EMPTY_TODAY, todayOnly.snapshot.lessonList.single().kind)
    }

    @Test
    fun `mixed official and pending lessons are ordered and respect hidden teachers`() {
        val result = selector.select(
            schedule = listOf(day(
                TODAY,
                lesson(2, "14:00", "15:30", "Физика", "Преподаватель физики"),
                lesson(1, "09:30", "10:30", "Математика", "Преподаватель математики")
            )),
            now = at("10:00"),
            preferences = preferences(hideTeacher = true),
            pendingSport = listOf(pending(2, "13:00", prediction = true), pending(1, "11:00"))
        )

        assertEquals("Математика", result.snapshot.singleLesson.lesson?.subject)
        assertEquals(3, result.snapshot.singleLesson.remainingLessons)
        assertNull(result.snapshot.singleLesson.lesson?.teacher)
        val displayed = result.snapshot.lessonList.mapNotNull { it.lesson }
        assertEquals(listOf("Математика", "Секция 1", "Секция 2", "Физика"), displayed.map { it.subject })
        assertTrue(displayed.all { it.teacher == null })
        assertEquals(
            listOf(null, ScheduleWidgetPendingStatus.WAITING, ScheduleWidgetPendingStatus.PREDICTED, null),
            displayed.map { it.pendingStatus }
        )
    }

    @Test
    fun `pending times normalize to widget offset while started expired and invalid entries are excluded`() {
        val future = pending(1, "11:00").copy(
            start = OffsetDateTime.parse("2026-08-10T08:00:00Z"),
            end = OffsetDateTime.parse("2026-08-10T09:30:00Z"),
            sectionName = "  Плавание  ",
            teacherFio = "  Тестовый преподаватель  ",
            roomName = "  Бассейн  "
        )
        val result = selector.select(
            schedule = emptyList(),
            now = at("10:00"),
            preferences = preferences(),
            pendingSport = listOf(
                future,
                pending(2, "10:00"),
                pending(3, "08:00"),
                pending(4, "12:00").let { it.copy(end = it.start) },
                pending(5, "11:00", date = TODAY.plusDays(2))
            )
        )

        val displayed = result.snapshot.lessonList.mapNotNull { it.lesson }.single()
        assertEquals("Плавание", displayed.subject)
        assertEquals("11:00", displayed.start)
        assertEquals("12:30", displayed.end)
        assertEquals("Тестовый преподаватель", displayed.teacher)
        assertEquals("Бассейн", displayed.room)
        assertEquals(displayed, result.snapshot.singleLesson.lesson)
        assertEquals(0, result.snapshot.singleLesson.remainingLessons)
    }

    @Test
    fun `smart refresh reaches pending start before current official lesson ends`() {
        val official = listOf(day(TODAY, lesson(1, "09:30", "11:30", "Математика")))
        val waiting = pending(1, "10:05")
        val result = selector.select(official, at("10:00"), preferences(), listOf(waiting))

        assertEquals(Duration.ofMinutes(5), result.nextUpdateDelay)
        assertEquals(at("10:05").toInstant().toString(), result.snapshot.pendingValidUntil)

        val started = selector.select(official, at("10:05"), preferences(), listOf(waiting))
        assertEquals(listOf("Математика"), started.snapshot.lessonList.mapNotNull { it.lesson?.subject })
        assertNull(started.snapshot.officialFallback)
        assertNull(started.snapshot.pendingValidUntil)
    }

    @Test
    fun `pending replay validity is capped at seven minutes or its earliest start`() {
        val now = at("10:00")
        val later = selector.select(emptyList(), now, preferences(), listOf(pending(1, "11:00")))
        assertEquals(now.plusMinutes(7).toInstant().toString(), later.snapshot.pendingValidUntil)

        val sooner = selector.select(
            emptyList(), now, preferences(), listOf(pending(1, "11:00"), pending(2, "10:03"))
        )
        assertEquals(now.plusMinutes(3).toInstant().toString(), sooner.snapshot.pendingValidUntil)
    }

    @Test
    fun `official fallback restores exact next lesson list and remaining count rather than filtering pending rows`() {
        val schedule = listOf(day(
            TODAY,
            lesson(2, "14:00", "15:30", "Физика"),
            lesson(1, "12:00", "13:30", "Математика")
        ))
        val official = selector.select(schedule, at("10:00"), preferences()).snapshot
        val mixed = selector.select(
            schedule, at("10:00"), preferences(), listOf(pending(1, "11:00"), pending(2, "13:45"))
        ).snapshot

        assertEquals("Секция 1", mixed.singleLesson.lesson?.subject)
        assertEquals(3, mixed.singleLesson.remainingLessons)
        assertEquals(official, mixed.officialFallback)
        assertEquals(official, mixed.withoutPendingSport())
        assertEquals("Математика", mixed.withoutPendingSport().singleLesson.lesson?.subject)
        assertEquals(1, mixed.withoutPendingSport().singleLesson.remainingLessons)
        assertEquals(
            listOf("Математика", "Физика"),
            mixed.withoutPendingSport().lessonList.mapNotNull { it.lesson?.subject }
        )
        assertNull(official.officialFallback)
        assertNull(official.pendingValidUntil)
    }

    private fun pending(
        id: Long,
        start: String,
        prediction: Boolean = false,
        date: LocalDate = TODAY,
    ): PendingSportBooking {
        val startsAt = OffsetDateTime.of(date, LocalTime.parse(start), OFFSET)
        return PendingSportBooking(
            queueId = id,
            queueKind = if (prediction) PendingSportBooking.QueueKind.AUTO else PendingSportBooking.QueueKind.FREE,
            lessonId = if (prediction) -id else id,
            sectionName = "Секция $id",
            start = startsAt,
            end = startsAt.plusMinutes(90),
            teacherFio = "Тестовый преподаватель",
            roomName = "Тестовый корпус",
            isPrediction = prediction
        )
    }

    private fun preferences(
        smartScheduling: Boolean = true,
        forwardScheduling: Boolean = false,
        hideTeacher: Boolean = false,
        hidePreviousLessons: Boolean = false,
        showTomorrowWhenFinished: Boolean = false,
    ) = ScheduleWidgetPreferences(
        smartScheduling = smartScheduling,
        forwardScheduling = forwardScheduling,
        hideTeacher = hideTeacher,
        hidePreviousLessons = hidePreviousLessons,
        showTomorrowWhenFinished = showTomorrowWhenFinished,
        singleLessonStyle = LessonStyle.DOT,
        lessonListStyle = LessonStyle.DOT
    )

    private fun at(time: String): OffsetDateTime {
        return OffsetDateTime.of(TODAY, LocalTime.parse(time), OFFSET)
    }

    private fun day(date: LocalDate, vararg lessons: Lesson): DaySchedule {
        return DaySchedule(
            dayNumber = date.dayOfWeek.value,
            weekNumber = 1,
            date = date,
            note = null,
            lessons = lessons.toList()
        )
    }

    private fun lesson(
        id: Long,
        start: String,
        end: String,
        subject: String,
        teacher: String? = null,
    ): Lesson {
        return Lesson(
            pairId = id,
            start = LocalTime.parse(start),
            end = LocalTime.parse(end),
            type = "Лекция",
            typeId = Lesson.TypeId(1),
            note = null,
            subjectName = subject,
            subjectId = id,
            groupName = "M0000",
            flowId = id,
            flowTypeId = 2,
            teacherIsu = null,
            teacherFio = teacher,
            room = null,
            building = null,
            buildingId = null,
            mainBuildingId = null,
            format = "Очно",
            formatId = 1,
            zoomUrl = null,
            zoomPassword = null,
            zoomInfo = null
        )
    }

    private companion object {
        val TODAY: LocalDate = LocalDate.of(2026, 8, 10)
        val OFFSET = java.time.ZoneOffset.ofHours(3)
    }
}
