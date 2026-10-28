package dev.alllexey.itmowidgets.feature.schedule.domain.widget

import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetTimelineSamples.LABELS
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetTimelineSamples.ZONE
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetTimelineSamples.preferences
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetTimelineSamples.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toInstant

/** The sample Monday 7 September and its Tuesday ([SchedulePreviewScenario]). */
class CardsWidgetPagesTest {

    private val days = scenario.days(LABELS)

    @Test
    fun theFirstPageIsTodayThenTheSixDaysAfterIt() {
        val snapshot = ScheduleWidgetSelector().select(days, at("12:22"), ZONE, preferences()).snapshot

        assertEquals((7..13).map { "2026-09-${it.toString().padStart(2, '0')}" }, snapshot.cardsPages().map { it.date })
        assertEquals(0, snapshot.cardsFirstOffset())
    }

    @Test
    fun onceTodayIsOverTheFirstPageIsTomorrow() {
        val snapshot = ScheduleWidgetSelector().select(days, at("19:40"), ZONE, preferences()).snapshot

        assertEquals((8..13).map { "2026-09-${it.toString().padStart(2, '0')}" }, snapshot.cardsPages().map { it.date })
        assertEquals(1, snapshot.cardsFirstOffset())
    }

    @Test
    fun aSnapshotWithoutTheWeekHasOnlyItsDay() {
        val snapshot = ScheduleWidgetSnapshot.loading()

        assertEquals(listOf(snapshot.day), snapshot.cardsPages())
        assertEquals(0, snapshot.cardsFirstOffset())
    }

    @Test
    fun aPageHoldsForFiveMinutesAfterTheTap() {
        val page = CardsWidgetPage(page = 2, at = at("12:00"))

        assertEquals(2, page.current(at("12:04"), ZONE))
        assertEquals(1.minutes, page.resetIn(at("12:04"), ZONE))
        assertEquals(0, page.current(at("12:00") + 5.minutes, ZONE))
        assertNull(page.resetIn(at("12:05"), ZONE))
    }

    @Test
    fun aPageEndsAtMidnightAndWhenTheClockGoesBack() {
        val late = CardsWidgetPage(page = 3, at = at("23:58"))

        assertEquals(3, late.current(at("23:59"), ZONE))
        assertEquals(0, late.current(LocalDateTime(LocalDate(2026, 9, 8), LocalTime(0, 0)).toInstant(ZONE) + 1.seconds, ZONE))
        assertEquals(0, late.current(at("23:57"), ZONE))
    }

    private fun at(time: String): Instant = ScheduleWidgetTimelineSamples.at(LocalDate(2026, 9, 7), time)
}
