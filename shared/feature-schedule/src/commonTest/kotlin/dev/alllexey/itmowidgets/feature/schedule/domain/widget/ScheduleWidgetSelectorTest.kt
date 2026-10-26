package dev.alllexey.itmowidgets.feature.schedule.domain.widget

import dev.alllexey.itmowidgets.core.lesson.LessonChange
import dev.alllexey.itmowidgets.core.lesson.LessonDay
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetTimelineSamples.LABELS
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetTimelineSamples.ZONE
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetTimelineSamples.preferences
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetTimelineSamples.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant

/** The sample Monday of [SchedulePreviewScenario]: 08:20, 10:00 (moved), 11:40, 15:20 (online), 17:00 (sport). */
class ScheduleWidgetSelectorTest {

    private val selector = ScheduleWidgetSelector()
    private val days = scenario.days(LABELS)

    @Test
    fun beforeTheFirstLessonTheNextWidgetCountsDownToItAndTheDayIsPlanned() {
        val snapshot = select("08:05")

        assertEquals(NextLessonMoment.FIRST, snapshot.next.moment)
        assertEquals(LABELS.math, snapshot.next.lesson?.subject)
        assertEquals(at("08:20").toString(), snapshot.next.until)
        assertNull(snapshot.next.since)
        assertEquals(listOf(LABELS.physics, LABELS.programming), snapshot.next.rest.map { it.subject })
        assertEquals(DaySummary(DaySummaryKind.PLANNED, lessons = 4, sport = true, from = "08:20", to = "18:30"), snapshot.day.summary)
        assertEquals(485, snapshot.day.now)
        assertEquals(480, snapshot.day.axisStart)
    }

    @Test
    fun duringALessonTheNextWidgetShowsItWithItsProgressAndTheTwoAfterIt() {
        val snapshot = select("12:22")

        assertEquals(NextLessonMoment.NOW, snapshot.next.moment)
        assertEquals(WidgetLessonState.NOW, snapshot.next.lesson?.state)
        assertEquals(at("11:40").toString(), snapshot.next.since)
        assertEquals(at("13:10").toString(), snapshot.next.until)
        assertEquals(listOf(LABELS.english, LABELS.swimming), snapshot.next.rest.map { it.subject })
        assertEquals("18:30", snapshot.next.lastEnd)
        assertTrue(snapshot.isLive)
    }

    @Test
    fun theDayWidgetPlacesTheLessonsOnTheAxisWithTheNowLineAndTheGap() {
        val day = select("12:22").day

        assertEquals(WidgetContentKind.CONTENT, day.kind)
        assertEquals("2026-09-07", day.date)
        assertFalse(day.tomorrow)
        assertEquals(742, day.now)
        assertEquals(480, day.axisStart)
        assertEquals(18 * 60 + 35, day.axisEnd)
        assertEquals(listOf(WidgetGap(13 * 60 + 10, 15 * 60 + 20)), day.gaps)
        assertEquals(
            listOf(WidgetLessonState.DONE, WidgetLessonState.DONE, WidgetLessonState.NOW, WidgetLessonState.UPCOMING, WidgetLessonState.UPCOMING),
            day.lessons.map { it.state },
        )
        assertEquals(DaySummary(DaySummaryKind.REMAINING, lessons = 2, sport = true, to = "18:30"), day.summary)
        assertTrue(day.lessons[1].changed)
        assertTrue(day.lessons[3].online)
        assertNull(day.lessons[3].room)
        assertEquals("Иванов И. И.", day.lessons[0].teacher)
    }

    @Test
    fun inAGapOfAnHourOrMoreTheWaitIsAWindow() {
        val next = select("13:40").next

        assertEquals(NextLessonMoment.NEXT, next.moment)
        assertEquals(LABELS.english, next.lesson?.subject)
        assertTrue(next.window)
        assertEquals(listOf(LABELS.swimming), next.rest.map { it.subject })
    }

    @Test
    fun theEarlySwitchShowsTheNextLessonFifteenMinutesBeforeTheEnd() {
        val next = select("12:56", preferences(showNextLessonEarly = true)).next

        assertEquals(NextLessonMoment.NEXT, next.moment)
        assertEquals(LABELS.english, next.lesson?.subject)
        assertFalse(next.window)
        assertEquals(LABELS.programming, select("12:54", preferences(showNextLessonEarly = true)).next.lesson?.subject)
    }

    @Test
    fun afterTheLastLessonBothWidgetsShowTomorrowWithItsQueue() {
        val snapshot = select("19:40")

        assertEquals(NextLessonMoment.TOMORROW, snapshot.next.moment)
        assertEquals(LABELS.algorithms, snapshot.next.lesson?.subject)
        assertNull(snapshot.next.until)
        assertTrue(snapshot.next.todayHadLessons)
        assertTrue(snapshot.next.rest.last().queued)
        assertTrue(snapshot.day.tomorrow)
        assertNull(snapshot.day.now)
        assertEquals(DaySummaryKind.OVER, snapshot.day.summary?.kind)
        assertFalse(snapshot.isLive)
        // The queue has an official-only twin and holds until Android's next queue refresh.
        assertNotNull(snapshot.officialFallback)
        assertEquals(3, snapshot.day.lessons.size)
        assertEquals(2, snapshot.officialFallback?.day?.lessons?.size)
    }

    @Test
    fun withoutTomorrowTheDayStaysOnTodayAndHidingPastLessonsDropsThem() {
        val evening = select("19:40", preferences(showTomorrow = false)).day
        assertFalse(evening.tomorrow)
        assertEquals(DaySummaryKind.OVER, evening.summary?.kind)
        assertNull(evening.now)

        val hidden = select("12:22", preferences(hidePastLessons = true)).day
        assertEquals(listOf(LABELS.programming, LABELS.english, LABELS.swimming), hidden.lessons.map { it.subject })
        assertEquals(11 * 60, hidden.axisStart)
    }

    @Test
    fun aCancelledLessonIsNeverTheOneToGoTo() {
        val cancelled = days.map { day ->
            day.copy(lessons = day.lessons.map {
                if (it.subject == LABELS.programming) it.copy(change = LessonChange(DATE.atTime(9, 0), cancelled = true)) else it
            })
        }
        val snapshot = select("12:22", days = cancelled)

        assertEquals(LABELS.english, snapshot.next.lesson?.subject)
        assertTrue(snapshot.day.lessons.single { it.subject == LABELS.programming }.cancelled)
    }

    @Test
    fun noLessonsTodayOrTomorrowIsEmpty() {
        val snapshot = select("12:22", days = listOf(LessonDay(DATE, emptyList(), cachedAt = null)))

        assertEquals(WidgetContentKind.EMPTY, snapshot.next.kind)
        assertEquals(WidgetContentKind.EMPTY, snapshot.day.kind)
        assertEquals(DaySummaryKind.NONE_TODAY, snapshot.day.summary?.kind)
    }

    @Test
    fun theWeekHoldsTheSixDaysAfterTodayAsPlannedDays() {
        val week = select("12:22", preferences(hideTeacher = true)).week

        assertEquals((8..13).map { "2026-09-${it.toString().padStart(2, '0')}" }, week.map { it.date })
        val tomorrow = week.first()
        assertEquals(WidgetContentKind.CONTENT, tomorrow.kind)
        assertEquals(listOf(LABELS.algorithms, LABELS.history, LABELS.swimming), tomorrow.lessons.map { it.subject })
        assertTrue(tomorrow.lessons.all { it.state == WidgetLessonState.UPCOMING && it.teacher == null })
        assertEquals(DaySummary(DaySummaryKind.PLANNED, lessons = 2, sport = true, from = "10:00", to = "16:50"), tomorrow.summary)
        assertNull(tomorrow.now)
        assertTrue(week.drop(1).all { it.kind == WidgetContentKind.EMPTY })
        // The official twin pages without the queue.
        assertEquals(2, select("12:22").officialFallback?.week?.first()?.lessons?.size)
    }

    @Test
    fun theNextUpdateIsTheNextBoundary() {
        val selection = selector.select(days, at("12:22"), ZONE, preferences(showNextLessonEarly = true))

        assertEquals(33.minutes, selection.nextUpdateDelay)
    }

    private fun select(time: String, preferences: ScheduleWidgetPreferences = preferences(), days: List<LessonDay> = this.days) =
        selector.select(days, at(time), ZONE, preferences).snapshot

    private fun at(time: String): Instant {
        val (hour, minute) = time.split(":").map(String::toInt)
        return DATE.atTime(hour, minute).toInstant(ZONE)
    }

    private companion object {
        val DATE = LocalDate(2026, 9, 7)
    }
}
