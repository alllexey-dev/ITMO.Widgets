package dev.alllexey.itmowidgets.feature.schedule.ui.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class DayTimelineLayoutTest {

    @Test
    fun aShortDayStaysCompactAtTheTop() {
        val layout = DayTimelineLayout(listOf(590..680, 690..780), axisStart = 540, axisEnd = 810, height = 250f, minLesson = 22f, maxPerMinute = 0.5f)

        val (top, height) = layout.lesson(0)
        assertEquals(25f, top, 0.01f)
        assertEquals(45f, height, 0.01f)
        assertEquals(135f, layout.y(810), 0.01f)
    }

    @Test
    fun aFullDayStillFillsTheHeight() {
        val layout = DayTimelineLayout(listOf(500..590, 600..690, 1000..1090), axisStart = 480, axisEnd = 1140, height = 250f, minLesson = 22f, maxPerMinute = 0.5f)

        assertEquals(250f, layout.y(1140), 0.01f)
    }
}
