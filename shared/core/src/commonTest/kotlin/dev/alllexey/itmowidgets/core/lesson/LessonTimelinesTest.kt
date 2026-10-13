package dev.alllexey.itmowidgets.core.lesson

import dev.alllexey.itmowidgets.core.navigation.LessonDetailsArgs
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.shared.core.CoreStrings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

class LessonTimelinesTest {

    private val today = LocalDate(2026, 9, 7)

    private val day = listOf(
        lesson("08:20", "09:50"),
        lesson("10:00", "11:30", change = LessonChange(LocalDateTime(2026, 9, 6, 20, 14), previousRoom = "1506")),
        lesson("11:40", "13:10"),
        lesson("15:20", "16:50", online = true),
    )

    @Test
    fun todayMarksDoneNowAndNextAndWindowsOfAnHourOrMore() {
        val items = LessonTimelines.build(day.shuffled(), DayMoment.Today(LocalTime(12, 22)), today)

        val lessons = items.filterIsInstance<TimelineItem.Lesson>()
        assertEquals(
            listOf(LessonState.DONE, LessonState.DONE, LessonState.NOW, LessonState.NEXT),
            lessons.map { it.lesson.state },
        )
        val gap = assertIs<TimelineItem.Gap>(items[3])
        assertEquals(
            UiText.Res(CoreStrings.lesson_gap, listOf(UiText.Res(CoreStrings.duration_hours_minutes, listOf(2, 10)))),
            gap.label,
        )
        assertEquals(0f, gap.progress)
        // The rail under 11:40 spans the lesson only: a window follows it.
        assertEquals(42f / 90f, lessons[2].progress, 0.001f)
        // The rail under 08:20 spans the ten-minute break up to the next dot.
        assertEquals(1f, lessons[0].progress)
        assertEquals(listOf(true, true, true, false), lessons.map { it.reached })
    }

    @Test
    fun aLessonSaysItsPartsInOrderWithItsPills() {
        val lessons = LessonTimelines.build(day, DayMoment.Today(LocalTime(9, 0)), today).filterIsInstance<TimelineItem.Lesson>()

        val changed = lessons[1]
        assertEquals(listOf(LessonPillKind.NEXT, LessonPillKind.CHANGED), changed.lesson.pills.map { it.kind })
        assertEquals(UiText.Dynamic("1506"), changed.lesson.previousPlace)
        assertEquals(UiText.Res(CoreStrings.lesson_changed_yesterday, listOf("20:14")), changed.changeNote)
        assertEquals(
            UiText.Joined(listOf(UiText.Dynamic("2202"), UiText.Res(CoreStrings.schedule_building_kronva)), ", "),
            changed.lesson.place,
        )
        assertEquals(
            UiText.Joined(listOf(UiText.Res(CoreStrings.schedule_lesson_type_lecture), UiText.Dynamic("Белов П. И.")), ", "),
            changed.lesson.byline,
        )

        val online = lessons[3].lesson
        assertNull(online.place)
        assertEquals(listOf(LessonPillKind.ONLINE), online.pills.map { it.kind })
    }

    @Test
    fun anotherDayHasNoNowAndOnlyTheFirstLessonOfTomorrowIsNext() {
        val past = LessonTimelines.build(day, DayMoment.Past, today)
        assertEquals(setOf(1f), past.map { if (it is TimelineItem.Lesson) it.progress else (it as TimelineItem.Gap).progress }.toSet())

        val tomorrow = LessonTimelines.build(day, DayMoment.Future(firstIsNext = true), today).filterIsInstance<TimelineItem.Lesson>()
        assertEquals(LessonState.NEXT, tomorrow.first().lesson.state)
        assertEquals(setOf(LessonState.UPCOMING), tomorrow.drop(1).map { it.lesson.state }.toSet())
        assertEquals(setOf(0f), tomorrow.map { it.progress }.toSet())
    }

    @Test
    fun cancelledAndQueuedLessonsAreNeverNowOrNext() {
        val lessons = listOf(
            lesson("10:00", "11:30", change = LessonChange(LocalDateTime(2026, 9, 7, 8, 0), cancelled = true)),
            lesson("10:00", "11:30", sport = SportMark.QUEUED),
            lesson("12:00", "13:30", sport = SportMark.BOOKED),
        )
        val items = LessonTimelines.build(lessons, DayMoment.Today(LocalTime(10, 30)), today).filterIsInstance<TimelineItem.Lesson>()

        assertEquals(listOf(LessonState.UPCOMING, LessonState.UPCOMING, LessonState.NEXT), items.map { it.lesson.state })
        assertEquals(listOf(LessonPillKind.CANCELLED), items[0].lesson.pills.map { it.kind })
        assertEquals(true, items[0].lesson.cancelled)
        assertEquals(UiText.Res(CoreStrings.lesson_changed_today, listOf("08:00")), items[0].changeNote)
        assertEquals(listOf(LessonPillKind.QUEUE), items[1].lesson.pills.map { it.kind })
        assertEquals(listOf(LessonPillKind.NEXT, LessonPillKind.BOOKED), items[2].lesson.pills.map { it.kind })
    }

    @Test
    fun aPlaceholderRoomMakesTheLessonOnlineWithoutAPlaceLine() {
        val remote = lesson("10:00", "11:30").copy(room = " Дистант ", building = null, note = "дистанционно")
        val ui = LessonTimelines.lesson(remote, LessonState.UPCOMING)

        assertEquals(true, remote.isOnline)
        assertNull(ui.place)
        assertNull(ui.note)
        assertEquals(listOf(LessonPillKind.ONLINE), ui.pills.map { it.kind })

        val withNote = lesson("10:00", "11:30", online = true).copy(room = "онлайн", note = "Ссылка в чате")
        assertNull(LessonTimelines.lesson(withNote, LessonState.UPCOMING).place)
        assertEquals("Ссылка в чате", LessonTimelines.lesson(withNote, LessonState.UPCOMING).note)
    }

    @Test
    fun onlyPlaceholdersCountAsOnline() {
        listOf("дистант", "Дистанционно", "онлайн", "Online", "удалённо", "Zoom").forEach {
            assertEquals(true, isOnlinePlaceholder(it), it)
        }
        listOf(null, "", "2202", "Актовый зал", "Кронверкский пр., 49").forEach {
            assertEquals(false, isOnlinePlaceholder(it), it.toString())
        }
        assertEquals("дистант", lesson("10:00", "11:30").copy(note = "дистант").noteText)
    }

    @Test
    fun overlappingLessonsShareOneNowMarkerAndAMonotonicRailWithoutAWindow() {
        // Веб-программирование 15:30-17:00 and a room booking 16:00-20:30, at 16:30.
        val items = LessonTimelines.build(
            listOf(lesson("16:00", "20:30", subject = "Бронь"), lesson("15:30", "17:00", subject = "Веб")),
            DayMoment.Today(LocalTime(16, 30)),
            today,
        )

        val lessons = items.map { assertIs<TimelineItem.Lesson>(it) }
        assertEquals(listOf("Веб", "Бронь"), lessons.map { it.lesson.subject })
        assertEquals(listOf(LessonState.NOW, LessonState.NOW), lessons.map { it.lesson.state })
        // The earlier lesson's rail runs to its end: the moment is on it, the later one has not started its stretch.
        assertEquals(60f / 90f, lessons[0].progress, 0.001f)
        assertEquals(0f, lessons[1].progress)
        assertEquals(listOf(true, false), lessons.map { it.reached })
        assertEquals(1, lessons.count { it.progress > 0f && it.progress < 1f })
        assertNull(lessons[0].overlapNote)
        assertEquals(UiText.Res(CoreStrings.lesson_overlap, listOf("Веб")), lessons[1].overlapNote)

        val later = LessonTimelines.build(lessons.map { it.lesson.toDay() }, DayMoment.Today(LocalTime(18, 0)), today)
            .map { it as TimelineItem.Lesson }
        assertEquals(listOf(1f, 60f / 210f), later.map { it.progress })
    }

    @Test
    fun aNestedLessonAndIdenticalTimesKeepOneMarkerOnTheEarliestOngoing() {
        val nested = LessonTimelines.build(
            listOf(lesson("10:00", "14:00", subject = "Длинная"), lesson("11:00", "12:00", subject = "Внутри")),
            DayMoment.Today(LocalTime(11, 30)),
            today,
        ).map { it as TimelineItem.Lesson }
        assertEquals(listOf(90f / 240f, 0f), nested.map { it.progress })
        assertEquals(UiText.Res(CoreStrings.lesson_overlap, listOf("Длинная")), nested[1].overlapNote)

        val same = LessonTimelines.build(
            listOf(lesson("10:00", "11:30", subject = "А"), lesson("10:00", "11:30", subject = "Б")),
            DayMoment.Today(LocalTime(10, 45)),
            today,
        ).map { it as TimelineItem.Lesson }
        assertEquals(listOf(0.5f, 0f), same.map { it.progress })
        assertEquals(listOf(LessonState.NOW, LessonState.NOW), same.map { it.lesson.state })
        assertEquals(UiText.Res(CoreStrings.lesson_overlap, listOf("А")), same[1].overlapNote)
    }

    @Test
    fun aWindowAfterAnOverlapCountsFromTheLatestEnd() {
        val items = LessonTimelines.build(
            listOf(lesson("15:30", "17:00"), lesson("16:00", "20:30"), lesson("21:40", "23:00")),
            DayMoment.Today(LocalTime(21, 0)),
            today,
        )

        assertEquals(listOf(true, true, false, true), items.map { it is TimelineItem.Lesson })
        val gap = items[2] as TimelineItem.Gap
        assertEquals(
            UiText.Res(CoreStrings.lesson_gap, listOf(UiText.Res(CoreStrings.duration_hours_minutes, listOf(1, 10)))),
            gap.label,
        )
        assertEquals(30f / 70f, gap.progress, 0.001f)
        assertEquals(listOf(1f, 1f, 0f), items.filterIsInstance<TimelineItem.Lesson>().map { it.progress })
    }

    private fun LessonUi.toDay() = lesson(start, end, subject = subject)

    private fun lesson(
        start: String,
        end: String,
        online: Boolean = false,
        change: LessonChange? = null,
        sport: SportMark? = null,
        subject: String = "Физика",
    ) = DayLesson(
        start = LocalTime.parse(start),
        end = LocalTime.parse(end),
        subject = subject,
        typeId = 1,
        teacher = "Белов Павел Игоревич",
        room = if (online) null else "2202",
        building = if (online) null else "Кронверкский пр., 49",
        online = online,
        target = LessonTarget.Lesson(
            LessonDetailsArgs(1, "2026-09-07", "Физика", 1, "", start, end, null, null, null, null, null, null, null, null, null, null),
        ),
        change = change,
        sport = sport,
    )
}
