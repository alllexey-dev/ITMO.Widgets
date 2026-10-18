package dev.alllexey.itmowidgets.feature.sport.domain.widget

import dev.alllexey.itmowidgets.core.sport.SportScoreSummary
import dev.alllexey.itmowidgets.feature.sport.cards.SportCardFixtures
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportQueueEntryStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.asTimeZone

/** The fixtures' booking starts on Tuesday 8 September at 18:30 (UTC+3). */
class SportWidgetSelectorTest {

    private val selector = SportWidgetSelector()
    private val zone = UtcOffset(hours = 3).asTimeZone()
    private val start = SportCardFixtures.start
    private val booked = SportCardFixtures.booking(1)
    private val queued = SportCardFixtures.booking(2).copy(start = start + 2.days, end = start + 2.days + 90.minutes, signed = false, signEntry = SportCardFixtures.entry())
    private val score = SportScoreSummary(attendances = 15, bonus = 15)

    @Test
    fun theRingCountsVisitsAndTheCreditedBonus() {
        val snapshot = selector.select(SportScoreSummary(65, 52), emptyList(), start, zone)

        assertEquals(65, snapshot.visits)
        assertEquals(40, snapshot.bonus)
        assertEquals(105, snapshot.total)
        assertTrue(snapshot.done)
        assertEquals(0, snapshot.remaining)
    }

    @Test
    fun theNearestBookingsAndQueuesComeWithTheWordsOfSport() {
        val snapshot = selector.select(score, listOf(queued, booked), start - 1.days, zone)

        val items = snapshot.items!!
        assertEquals(listOf(1L, 2L), items.map { it.lessonId })
        assertEquals(SportWidgetDay.TOMORROW, items[0].day)
        assertEquals("18:30", items[0].time)
        assertEquals(SportWidgetStatus.BOOKED, items[0].status)
        assertEquals(SportWidgetDay.LATER, items[1].day)
        assertEquals(SportWidgetStatus.QUEUE, items[1].status)
        assertEquals(3 to 12, items[1].position to items[1].total)
        assertFalse(snapshot.done)
        assertEquals(70, snapshot.remaining)
    }

    @Test
    fun anEndedOrGivenUpLessonLeavesAndAnUnpublishedOneWaitsForTheSchedule() {
        val gaveUp = queued.copy(signEntry = SportCardFixtures.entry(SportQueueEntryStatus.GAVE_UP_NOTIFYING))
        val unpublished = queued.copy(lessonId = 3, isLessonReal = false)

        assertEquals(emptyList(), selector.select(score, listOf(booked, gaveUp), start + 2.hours, zone).items)
        assertEquals(SportWidgetStatus.UNPUBLISHED, selector.select(score, listOf(unpublished), start, zone).items!!.single().status)
    }

    @Test
    fun withoutAScoreTheWidgetIsAnErrorAndWithoutBookingsItShowsTheRingAlone() {
        assertEquals(SportWidgetKind.ERROR, selector.select(null, listOf(booked), start, zone).kind)
        assertNull(selector.select(score, null, start, zone).items)
    }

    @Test
    fun theTimelineChangesAtMidnightsAndBookingEnds() {
        val from = start - 1.days
        val timeline = selector.timeline(score, listOf(booked), from, from + 2.days, zone)

        assertEquals(
            listOf(SportWidgetDay.TOMORROW, SportWidgetDay.TODAY, null),
            timeline.entries.map { it.snapshot.items!!.firstOrNull()?.day },
        )
        assertEquals(timeline, SportWidgetTimelineJson.decode(SportWidgetTimelineJson.encode(timeline)))
    }
}
