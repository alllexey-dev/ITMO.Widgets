package dev.alllexey.itmowidgets.core.debug

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
    private val store = FakeSportLessonTemplateStore()
    private val provider = DefaultSportLessonTemplateProvider(timeProvider, store)

    @Test
    fun `returns no schedule when templates are disabled`() {
        assertNull(provider.getSchedule())
    }

    @Test
    fun `creates safe future lessons when templates are enabled`() {
        provider.setEnabled(true)

        val lessons = provider.getSchedule().orEmpty().values.flatten()

        assertEquals(6, lessons.size)
        assertTrue(lessons.all { it.lessonId < 0 })
        assertTrue(lessons.all { it.end > timeProvider.now() })
        assertTrue(lessons.any { it.available == 0 })
        assertTrue(lessons.any { it.available > 0 })
    }

    private class FakeSportLessonTemplateStore : SportLessonTemplateStore {
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
