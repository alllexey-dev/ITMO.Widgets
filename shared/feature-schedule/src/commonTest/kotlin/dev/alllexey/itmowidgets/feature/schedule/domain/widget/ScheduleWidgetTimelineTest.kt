package dev.alllexey.itmowidgets.feature.schedule.domain.widget

import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetTimelineSamples.DAY_AFTER
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetTimelineSamples.END_OF_TOMORROW
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetTimelineSamples.TODAY
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetTimelineSamples.TOMORROW
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetTimelineSamples.ZONE
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetTimelineSamples.at
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetTimelineSamples.day
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetTimelineSamples.lesson
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetTimelineSamples.pending
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetTimelineSamples.preferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class ScheduleWidgetTimelineTest {

    private val selector = ScheduleWidgetSelector()

    // Breaks, a pending row today and tomorrow, lessons around both sides of midnight and a day after tomorrow.
    private val schedule = listOf(
        day(
            TODAY,
            lesson(1, "09:30", "11:00", "Математика", teacher = "Тестовый преподаватель"),
            lesson(2, "11:20", "12:50", "Физика"),
            lesson(3, "15:00", "16:30", "История"),
            lesson(4, "23:10", "23:55", "Ночная практика")
        ),
        day(
            TOMORROW,
            lesson(5, "00:05", "00:40", "Полуночный семинар"),
            lesson(6, "10:00", "11:30", "Алгоритмы")
        ),
        day(DAY_AFTER, lesson(7, "08:20", "09:50", "Базы данных")),
    )
    private val pendingSport = listOf(
        pending(21, TODAY, "13:30", prediction = true),
        pending(22, TOMORROW, "18:00"),
    )
    private val from = at(TODAY, "08:00:17.5")

    @Test
    fun everyEntryMatchesSelectAtSampledInstants() {
        for (preferences in allDisplayPreferences()) {
            val timeline = timeline(preferences)
            for (instant in sampledInstants(timeline)) {
                val index = timeline.entries.indexOfLast { it.validFrom <= instant }
                val entry = assertNotNull(timeline.entryAt(instant), "entry at $instant")
                val selected = selector.select(schedule, instant, ZONE, preferences, pendingSport).snapshot
                assertEquals(
                    selected.copy(pendingValidUntil = null),
                    entry.snapshot.copy(pendingValidUntil = null),
                    "snapshot at $instant with $preferences"
                )
                val expectedValidity = selected.pendingValidUntil?.let { timeline.validityEnd(index).toString() }
                assertEquals(expectedValidity, entry.snapshot.pendingValidUntil, "pendingValidUntil at $instant")
            }
        }
    }

    @Test
    fun entriesStartAtFromAndAreOrderedWithoutEqualNeighbours() {
        val timeline = timeline(preferences(forwardScheduling = true, showTomorrowWhenFinished = true))

        assertEquals(1, timeline.version)
        assertEquals(from, timeline.generatedAt)
        assertEquals(from, timeline.entries.first().validFrom)
        assertEquals(END_OF_TOMORROW, timeline.validUntil)
        timeline.entries.zipWithNext { earlier, later ->
            assertTrue(earlier.validFrom < later.validFrom)
            assertTrue(earlier.snapshot.copy(pendingValidUntil = null) != later.snapshot.copy(pendingValidUntil = null))
        }
    }

    @Test
    fun pendingRowsHoldToTheEndOfTheirEntryAndLeaveAtTheirStart() {
        val timeline = timeline(preferences())
        val beforeStart = timeline.entryAt(at(TODAY, "13:29:59"))!!
        val atStart = timeline.entryAt(at(TODAY, "13:30"))!!

        assertEquals(
            ScheduleWidgetPendingStatus.PREDICTED,
            beforeStart.snapshot.lessonList.firstNotNullOf { it.lesson?.pendingStatus }
        )
        assertEquals(at(TODAY, "13:30").toString(), beforeStart.snapshot.pendingValidUntil)
        assertEquals(at(TODAY, "13:30"), atStart.validFrom)
        assertTrue(atStart.snapshot.lessonList.none { it.lesson?.pendingStatus == ScheduleWidgetPendingStatus.PREDICTED })
    }

    @Test
    fun compactEarlySwitchIsAnEntryOnlyWhenEnabled() {
        val switchAt = at(TODAY, "10:45")

        assertEquals(switchAt, timeline(preferences(forwardScheduling = true)).entryAt(switchAt)?.validFrom)
        assertTrue(timeline(preferences(forwardScheduling = false)).entries.none { it.validFrom == switchAt })
    }

    @Test
    fun midnightStartsTomorrowsDay() {
        val timeline = timeline(preferences(showTomorrowWhenFinished = true))
        val midnight = at(TOMORROW, "00:00")
        val entry = timeline.entryAt(midnight)!!

        assertEquals(midnight, entry.validFrom)
        assertEquals(TOMORROW.toString(), entry.snapshot.lessonList.first().dateIso)
        assertEquals("Полуночный семинар", entry.snapshot.singleLesson.lesson?.subject)
    }

    @Test
    fun noEntryOutsideTheTimeline() {
        val timeline = timeline(preferences())

        assertNull(timeline.entryAt(from - 1.nanoseconds))
        assertNull(timeline.entryAt(END_OF_TOMORROW))
        assertNotNull(timeline.entryAt(END_OF_TOMORROW - 1.nanoseconds))
    }

    @Test
    fun anEmptyRangeIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            selector.timeline(schedule, from, from, ZONE, preferences(), pendingSport)
        }
    }

    private fun timeline(preferences: ScheduleWidgetPreferences) =
        selector.timeline(schedule, from, END_OF_TOMORROW, ZONE, preferences, pendingSport)

    private fun allDisplayPreferences(): List<ScheduleWidgetPreferences> = listOf(false, true).flatMap { forward ->
        listOf(false, true).flatMap { hidePast ->
            listOf(false, true).map { tomorrow ->
                preferences(forwardScheduling = forward, hidePreviousLessons = hidePast, showTomorrowWhenFinished = tomorrow)
            }
        }
    }

    /** Every 37 s, and both sides of every entry start, lesson start and end, early switch and pending start. */
    private fun sampledInstants(timeline: ScheduleWidgetTimeline): List<Instant> {
        val regular = generateSequence(from) { it + 37.seconds }.takeWhile { it < timeline.validUntil }
        val lessonEdges = schedule.flatMap { day ->
            day.lessons.flatMap { lesson ->
                val end = at(day.date, lesson.end.toString())
                listOf(at(day.date, lesson.start.toString()), end, end - 15.minutes)
            }
        }
        val edges = timeline.entries.map { it.validFrom } + lessonEdges + pendingSport.map { it.start } +
            listOf(at(TOMORROW, "00:00"), at(TOMORROW, "00:00") - 15.minutes)
        val bothSides = edges.flatMap { listOf(it, it - 1.nanoseconds) }
            .filter { it >= from && it < timeline.validUntil }
        return (regular + bothSides).toList()
    }
}
