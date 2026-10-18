package dev.alllexey.itmowidgets.feature.schedule.domain.widget

import dev.alllexey.itmowidgets.core.settings.CompactScheduleWidgetSettings
import dev.alllexey.itmowidgets.core.settings.FullScheduleWidgetSettings
import dev.alllexey.itmowidgets.core.settings.ScheduleWidgetSettings
import dev.alllexey.itmowidgets.core.settings.WidgetTextSize
import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleWidgetSnapshotTest {

    private val official = ScheduleWidgetSnapshot(
        NextLessonWidgetContent(WidgetContentKind.EMPTY),
        DayWidgetContent(WidgetContentKind.EMPTY, date = "2026-09-07"),
    )
    private val pending = official.copy(officialFallback = official, pendingValidUntil = "2026-09-07T09:30:00Z")

    @Test
    fun `loading and error snapshots cannot replace a refresh failure, content can`() {
        assertFalse(ScheduleWidgetSnapshot.loading().canBeShownWhenRefreshFails())
        assertFalse(ScheduleWidgetSnapshot.error().canBeShownWhenRefreshFails())
        assertTrue(official.canBeShownWhenRefreshFails())
    }

    @Test
    fun `a queue stays only while it is on and strictly before its deadline`() {
        val before = Instant.parse("2026-09-07T09:29:59Z")
        val at = Instant.parse("2026-09-07T09:30:00Z")

        assertSame(pending, pending.forPendingAvailability(enabled = true, now = before))
        assertSame(official, pending.forPendingAvailability(enabled = false, now = before))
        assertSame(official, pending.forPendingAvailability(enabled = true, now = at))
        assertSame(official, pending.copy(pendingValidUntil = "soon").forPendingAvailability(enabled = true, now = before))
    }

    @Test
    fun `the text sizes reach the official twin`() {
        val sized = pending.withTextSizes(
            ScheduleWidgetSettings(
                compact = CompactScheduleWidgetSettings(textSize = WidgetTextSize.LARGE),
                full = FullScheduleWidgetSettings(textSize = WidgetTextSize.EXTRA_LARGE),
            )
        )

        assertEquals(WidgetTextSize.LARGE, sized.officialFallback?.compactTextSize)
        assertEquals(WidgetTextSize.EXTRA_LARGE, sized.officialFallback?.fullTextSize)
    }
}
