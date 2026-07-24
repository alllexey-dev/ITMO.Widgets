package dev.alllexey.itmowidgets.feature.sport.data.debug

import dev.alllexey.itmowidgets.core.debug.SportLessonTemplateController
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

class DefaultSportLessonTemplateProviderTest {

    private val timeProvider = FakeAcademicTimeProvider()
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

    private class FakeSportLessonTemplateController : SportLessonTemplateController {
        private var enabled = false

        override fun isEnabled(): Boolean = enabled

        override fun setEnabled(enabled: Boolean) {
            this.enabled = enabled
        }
    }

    private class FakeAcademicTimeProvider : AcademicTimeProvider {
        override val zoneId: ZoneId = ZoneId.of("Europe/Moscow")

        override fun today(): LocalDate = LocalDate.of(2026, 7, 18)

        override fun now(): OffsetDateTime = OffsetDateTime.parse("2026-07-18T16:30:00+03:00")
    }
}
