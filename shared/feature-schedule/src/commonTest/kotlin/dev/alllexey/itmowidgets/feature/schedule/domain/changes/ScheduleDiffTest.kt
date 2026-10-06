package dev.alllexey.itmowidgets.feature.schedule.domain.changes

import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeField.FORMAT
import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeField.PLACE
import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeField.TEACHER
import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeField.TIME
import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeKind.ADDED
import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeKind.CANCELLED
import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeKind.UPDATED
import dev.alllexey.itmowidgets.feature.schedule.plusMinutes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.atTime
import kotlinx.datetime.plus

class ScheduleDiffTest {

    @Test
    fun identicalSnapshotsAndReorderedLessonsOrDaysAreNoChange() {
        val lessons = listOf(lesson(1, TUE), lesson(2, TUE, at(10, 0)), lesson(3, WED), lesson(4, FRI))

        assertEquals(emptyList<DetectedChange>(), compare(snapshot(lessons), snapshot(lessons)))
        assertEquals(emptyList<DetectedChange>(), compare(snapshot(lessons), snapshot(lessons.reversed())))
        assertEquals(emptyList<DetectedChange>(),
            compare(snapshot(lessons), snapshot(listOf(lessons[3], lessons[1], lessons[2], lessons[0]))))
    }

    @Test
    fun aNewLessonInTheOverlapIsAddedAndOneOnTheNewLastDayOfTheWindowIsNot() {
        val added = lesson(2, WED)

        assertEquals(listOf(DetectedChange(ADDED, emptySet(), null, added)),
            compare(snapshot(listOf(lesson(1, TUE))), snapshot(listOf(lesson(1, TUE), added))))
        assertEquals(emptyList<DetectedChange>(), compare(
            snapshot(listOf(lesson(1, TUE)), start = SUN),
            snapshot(listOf(lesson(1, TUE), lesson(2, LAST_DAY)))
        ))
    }

    @Test
    fun aLessonGoneFromTheOverlapIsCancelledAndOneFromADayThatLeftTheWindowIsNot() {
        val gone = lesson(2, WED)

        assertEquals(listOf(DetectedChange(CANCELLED, emptySet(), gone, null)),
            compare(snapshot(listOf(lesson(1, TUE), gone)), snapshot(listOf(lesson(1, TUE)))))
        assertEquals(emptyList<DetectedChange>(), compare(
            snapshot(listOf(lesson(3, SUN), lesson(1, TUE)), start = SUN),
            snapshot(listOf(lesson(1, TUE)))
        ))
    }

    @Test
    fun theSamePairIdOnAnotherDayIsOneMoveWhereverTheDatesAre() {
        val before = lesson(1, TUE)
        val moved = lesson(1, WED)
        assertEquals(listOf(DetectedChange(UPDATED, setOf(TIME), before, moved)),
            compare(snapshot(listOf(before)), snapshot(listOf(moved))))

        val intoLastDay = lesson(1, LAST_DAY)
        assertEquals(listOf(DetectedChange(UPDATED, setOf(TIME), before, intoLastDay)),
            compare(snapshot(listOf(before), start = SUN), snapshot(listOf(intoLastDay))))

        val yesterday = lesson(1, SUN)
        val thursday = lesson(1, THU)
        assertEquals(listOf(DetectedChange(UPDATED, setOf(TIME), yesterday, thursday)),
            compare(snapshot(listOf(yesterday), start = SUN), snapshot(listOf(thursday))))
    }

    @Test
    fun aNewPairIdOfTheSameSubjectFlowAndTypeOnAnotherDayIsOneMove() {
        val before = lesson(1, TUE, subjectId = 10)
        val after = lesson(21, THU, subjectId = 10)

        assertEquals(listOf(DetectedChange(UPDATED, setOf(TIME), before, after)),
            compare(snapshot(listOf(before)), snapshot(listOf(after))))
    }

    @Test
    fun twoGoneAndOneNewOfOneKeyLinkTheEarlierAndCancelTheOtherInAStableOrder() {
        val tuesday = lesson(1, TUE, subjectId = 10)
        val wednesday = lesson(2, WED, subjectId = 10)
        val friday = lesson(21, FRI, subjectId = 10)
        val expected = listOf(
            DetectedChange(UPDATED, setOf(TIME), tuesday, friday),
            DetectedChange(CANCELLED, emptySet(), wednesday, null)
        )

        assertEquals(expected, compare(snapshot(listOf(tuesday, wednesday)), snapshot(listOf(friday))))
        assertEquals(expected, compare(snapshot(listOf(wednesday, tuesday)), snapshot(listOf(friday))))
    }

    @Test
    fun aLessonRecreatedWithANewPairIdAtTheSameSlotIsNoChange() {
        assertEquals(emptyList<DetectedChange>(), compare(
            snapshot(listOf(lesson(1, TUE, subjectId = 10))),
            snapshot(listOf(lesson(31, TUE, subjectId = 10)))
        ))
    }

    @Test
    fun aWeeklyLessonEnteringOnTheNewLastDayDoesNotPairWithALessonGoneFromTheOverlap() {
        val tuesday = lesson(1, TUE, at(14, 0), subjectId = 10)
        val monday = lesson(2, NEXT_MON, subjectId = 10)
        val nextTuesday = lesson(3, NEXT_TUE, at(14, 0), subjectId = 10)

        assertEquals(listOf(DetectedChange(CANCELLED, emptySet(), monday, null)), ScheduleDiff.compare(
            snapshot(listOf(tuesday, monday)),
            snapshot(listOf(tuesday, nextTuesday), start = TUE),
            TUE.atTime(12, 0)
        ))
    }

    @Test
    fun aRoomOrBuildingChangeIsAPlaceChangeAndCaseOrSpacingIsNot() {
        val before = lesson(1, TUE, room = "1506", building = "Кронверкский проспект, 49")

        assertEquals(setOf(PLACE), fieldsOf(before, before.copy(room = "2202")))
        assertEquals(setOf(PLACE), fieldsOf(before, before.copy(building = "Ломоносова, 9")))
        assertEquals(emptyList<DetectedChange>(), compare(
            snapshot(listOf(before)),
            snapshot(listOf(before.copy(room = " 1506 ", building = "кронверкский  Проспект, 49")))
        ))
        assertEquals(setOf(PLACE), fieldsOf(before, before.copy(room = null)))
    }

    @Test
    fun goingOnlineWithoutARoomIsOneChangeOfFormatAndPlace() {
        val before = lesson(1, TUE, formatId = 1, room = "1506")

        assertEquals(listOf(DetectedChange(UPDATED, setOf(FORMAT, PLACE), before, before.copy(formatId = 3, room = null))),
            compare(snapshot(listOf(before)), snapshot(listOf(before.copy(formatId = 3, room = null)))))
    }

    @Test
    fun teachersCompareByISUWhenBothHaveOneAndByNameOtherwise() {
        val before = lesson(1, TUE, teacherIsu = 1, teacherName = "Тестовый преподаватель")

        assertEquals(setOf(TEACHER), fieldsOf(before, before.copy(teacherIsu = 2)))
        assertEquals(emptyList<DetectedChange>(),
            compare(snapshot(listOf(before)), snapshot(listOf(before.copy(teacherIsu = null)))))
        val noIsu = before.copy(teacherIsu = null)
        assertEquals(setOf(TEACHER), fieldsOf(noIsu, noIsu.copy(teacherName = "Другой преподаватель")))
    }

    @Test
    fun aMoveToAnotherRoomAtOnceIsOneChangeOfTimeAndPlace() {
        val before = lesson(1, TUE, room = "1506")

        assertEquals(setOf(TIME, PLACE), fieldsOf(before, before.copy(start = at(10, 0), end = at(11, 30), room = "2202")))
    }

    @Test
    fun changesOfLessonsThatAreOverAreDroppedAndAMoveOutOfThePastIsKept() {
        val morning = lesson(1, MON, at(8, 20))
        assertEquals(emptyList<DetectedChange>(), compare(snapshot(listOf(morning)), snapshot(emptyList())))

        val endsNow = lesson(2, MON, at(10, 30), end = at(12, 0))
        assertEquals(emptyList<DetectedChange>(), compare(snapshot(listOf(endsNow)), snapshot(emptyList())))

        val tomorrow = morning.copy(date = TUE)
        assertEquals(listOf(DetectedChange(UPDATED, setOf(TIME), morning, tomorrow)),
            compare(snapshot(listOf(morning)), snapshot(listOf(tomorrow))))

        assertEquals(emptyList<DetectedChange>(), compare(snapshot(emptyList()), snapshot(listOf(morning))))
    }

    @Test
    fun snapshotsWithoutAnOverlapAreNoChange() {
        val old = LocalDate(2026, 8, 20)

        assertEquals(emptyList<DetectedChange>(), compare(
            snapshot(listOf(lesson(1, old.plus(1, DateTimeUnit.DAY))), start = old),
            snapshot(listOf(lesson(2, TUE)))
        ))
    }

    @Test
    fun changesAreOrderedByTheEarliestStartThenTheSubjectThenThePairId() {
        val changes = compare(
            snapshot(listOf(lesson(5, THU, subjectName = "Физика"))),
            snapshot(listOf(
                lesson(4, WED, subjectName = "Физика"),
                lesson(3, TUE, at(10, 0), subjectName = "Алгебра"),
                lesson(2, TUE, at(10, 0), subjectName = "Физика", subjectId = 20),
                lesson(1, TUE, at(10, 0), subjectName = "Физика", subjectId = 21)
            ))
        )

        assertEquals(listOf(3L, 1L, 2L, 4L, 5L), changes.map { (it.after ?: it.before)!!.pairId })
    }

    private fun fieldsOf(before: SnapshotLesson, after: SnapshotLesson) =
        compare(snapshot(listOf(before)), snapshot(listOf(after))).single().fields

    private fun compare(previous: ScheduleSnapshot, current: ScheduleSnapshot) = ScheduleDiff.compare(previous, current, NOW)

    private fun snapshot(lessons: List<SnapshotLesson>, start: LocalDate = MON) =
        ScheduleSnapshot(start, start.plus(7, DateTimeUnit.DAY), lessons)

    private fun lesson(
        pairId: Long,
        date: LocalDate,
        start: LocalTime = at(8, 20),
        end: LocalTime = start.plusMinutes(90),
        subjectId: Long = pairId,
        subjectName: String = "Предмет $subjectId",
        typeId: Int = 1,
        flowId: Long = subjectId,
        teacherIsu: Long? = 300001,
        teacherName: String? = "Тестовый преподаватель",
        room: String? = "1506",
        building: String? = "Кронверкский проспект, 49",
        formatId: Int = 1
    ) = SnapshotLesson(
        pairId = pairId, date = date, start = start, end = end, subjectId = subjectId, subjectName = subjectName,
        typeId = typeId, flowId = flowId, flowName = "Поток $flowId", teacherIsu = teacherIsu, teacherName = teacherName,
        room = room, building = building, formatId = formatId, format = if (formatId == 3) "Дистанционный" else "Очный"
    )

    private fun at(hour: Int, minute: Int): LocalTime = LocalTime(hour, minute)

    private companion object {
        val SUN: LocalDate = LocalDate(2026, 9, 6)
        val MON: LocalDate = LocalDate(2026, 9, 7)
        val TUE: LocalDate = LocalDate(2026, 9, 8)
        val WED: LocalDate = LocalDate(2026, 9, 9)
        val THU: LocalDate = LocalDate(2026, 9, 10)
        val FRI: LocalDate = LocalDate(2026, 9, 11)
        val NEXT_MON: LocalDate = LocalDate(2026, 9, 14)
        val NEXT_TUE: LocalDate = LocalDate(2026, 9, 15)
        val LAST_DAY: LocalDate = NEXT_MON
        val NOW: LocalDateTime = MON.atTime(12, 0)
    }
}
