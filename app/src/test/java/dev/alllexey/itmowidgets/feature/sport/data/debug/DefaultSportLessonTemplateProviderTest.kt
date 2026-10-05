package dev.alllexey.itmowidgets.feature.sport.data.debug

import dev.alllexey.itmowidgets.core.testing.FakeSportLessonTemplateController
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultSportLessonTemplateProviderTest {

    private val timeProvider = FixedAcademicTime(LocalDateTime.of(2026, 7, 18, 16, 30))
    private val controller = FakeSportLessonTemplateController()
    private val provider = DefaultSportLessonTemplateProvider(timeProvider, controller)

    @Test
    fun `returns no schedule when templates are disabled`() {
        assertNull(provider.getSchedule())
    }

    @Test
    fun `creates safe future lessons when templates are enabled`() {
        controller.setEnabled(true)

        val lessons = provider.getSchedule().orEmpty().values.flatten()

        assertEquals(6, lessons.size)
        assertTrue(lessons.all { it.lessonId < 0 })
        assertTrue(lessons.all { it.end > timeProvider.now() })
        assertTrue(lessons.any { it.available == 0 })
        assertTrue(lessons.any { it.available > 0 })
    }
}
