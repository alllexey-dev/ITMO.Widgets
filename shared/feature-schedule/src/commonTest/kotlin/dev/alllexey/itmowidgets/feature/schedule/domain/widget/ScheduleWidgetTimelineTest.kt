package dev.alllexey.itmowidgets.feature.schedule.domain.widget

import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetTimelineSamples.LABELS
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetTimelineSamples.ZONE
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetTimelineSamples.preferences
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetTimelineSamples.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class ScheduleWidgetTimelineTest {

    private val timeline = ScheduleWidgetTimelineSamples.fixtureTimeline()

    @Test
    fun everyEntryIsTheSelectionAtItsStartAndAtItsTicks() {
        val selector = ScheduleWidgetSelector()
        val days = scenario.days(LABELS)
        timeline.entries.forEachIndexed { index, entry ->
            val selected = selector.select(days, entry.validFrom, ZONE, preferences()).snapshot
            assertEquals(selected.copy(pendingValidUntil = null), entry.snapshot.copy(pendingValidUntil = null))
            entry.nowTicks.forEach { tick ->
                assertTrue(tick.at > entry.validFrom && tick.at < timeline.validityEnd(index))
                assertEquals(selector.select(days, tick.at, ZONE, preferences()).snapshot.day.now, tick.minute)
            }
        }
    }

    @Test
    fun theNowLineMovesByTicksInsideOneEntry() {
        val first = timeline.entries.first()

        assertEquals(742, first.snapshot.day.now)
        assertEquals(745, first.nowTicks.first().minute)
        assertTrue(first.nowTicks.zipWithNext().all { (a, b) -> b.at - a.at == 5.minutes })
    }

    @Test
    fun entriesChangeOnlyWhereTheWidgetsChange() {
        val boundaries = timeline.entries.map { it.validFrom }

        assertEquals(boundaries.sorted(), boundaries)
        assertTrue(timeline.entries.zipWithNext().all { (a, b) -> a.snapshot != b.snapshot })
        assertTrue(timeline.entries.last().nowTicks.isEmpty())
        assertEquals(timeline.validUntil, timeline.validityEnd(timeline.entries.lastIndex))
    }
}
