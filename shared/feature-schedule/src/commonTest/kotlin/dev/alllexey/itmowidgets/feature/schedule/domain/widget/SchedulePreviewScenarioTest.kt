package dev.alllexey.itmowidgets.feature.schedule.domain.widget

import dev.alllexey.itmowidgets.core.settings.CompactScheduleWidgetSettings
import dev.alllexey.itmowidgets.core.settings.FullScheduleWidgetSettings
import dev.alllexey.itmowidgets.core.settings.ScheduleWidgetSettings
import dev.alllexey.itmowidgets.core.settings.WidgetTextSize
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetTimelineSamples.LABELS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SchedulePreviewScenarioTest {
    private val scenario = SchedulePreviewScenario(ScheduleWidgetSelector())

    @Test
    fun theLessonMomentIsDuringTheThirdLessonAndTheEveningShowsTomorrow() {
        assertEquals(LABELS.programming, scenario.snapshot(ScheduleWidgetSettings(), SchedulePreviewMoment.LESSON, LABELS).next.lesson?.subject)
        assertEquals(LABELS.algorithms, scenario.snapshot(ScheduleWidgetSettings(), SchedulePreviewMoment.EVENING, LABELS).next.lesson?.subject)
    }

    @Test
    fun theSettingsReachTheSampleSoThePreviewsShowThem() {
        val snapshot = scenario.snapshot(
            ScheduleWidgetSettings(
                compact = CompactScheduleWidgetSettings(textSize = WidgetTextSize.EXTRA_LARGE),
                full = FullScheduleWidgetSettings(hideTeacher = true, textSize = WidgetTextSize.LARGE),
            ),
            SchedulePreviewMoment.LESSON,
            LABELS,
        )

        assertEquals(WidgetTextSize.EXTRA_LARGE, snapshot.compactTextSize)
        assertEquals(WidgetTextSize.LARGE, snapshot.fullTextSize)
        assertNull(snapshot.day.lessons.firstNotNullOfOrNull { it.teacher })
    }
}
