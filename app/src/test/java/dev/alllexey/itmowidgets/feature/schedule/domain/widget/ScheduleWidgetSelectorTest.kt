package dev.alllexey.itmowidgets.feature.schedule.domain.widget

import dev.alllexey.itmowidgets.core.settings.LessonStyle
import dev.alllexey.itmowidgets.feature.schedule.domain.model.DaySchedule
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Lesson
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
