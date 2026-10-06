package dev.alllexey.itmowidgets.feature.sport.presentation.sign

import dev.alllexey.itmowidgets.core.result.LoadState
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.feature.sport.cards.SportCardFixtures
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportLesson
import dev.alllexey.itmowidgets.feature.sport.presentation.FakeSportScheduleRepository
import java.time.LocalDateTime
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SportSharedLessonResolverTest {

    private val schedule = FakeSportScheduleRepository()
    private val resolver = SportSharedLessonResolver(schedule, FixedAcademicTime(LocalDateTime.of(2026, 9, 8, 12, 0)))

    private fun lessonOn(id: Long, day: Int, hour: Int = 18): SportLesson {
        val start = Instant.parse("2026-09-%02dT%02d:30:00+03:00".format(day, hour))
        return SportCardFixtures.lesson(id).copy(start = start, end = start + 90.minutes)
    }

    private fun SportLesson.predicted() = copy(isLessonReal = false, start = start + 14.days, end = end + 14.days)

    private suspend fun catalog(vararg lessons: SportLesson): List<SportLesson> =
        lessons.toList().also { schedule.schedule.emit(LoadState.Content(it)) }

    @Test
    fun `a real link opens its lesson`() = runTest {
        val lesson = lessonOn(5, day = 10)
        catalog(lessonOn(1, day = 9), lesson)

        assertEquals(SportSignEvent.OpenLessonDetails(lesson), resolver.resolve(5, predicted = false))
    }

    @Test
    fun `a predicted link opens the prediction, not its prototype`() = runTest {
        val prototype = lessonOn(7, day = 9)
        val lessons = catalog(prototype, prototype.predicted())

        assertEquals(SportSignEvent.OpenLessonDetails(prototype.predicted()), resolver.resolve(7, predicted = true))
        assertEquals(prototype.predicted(), resolver.linkedLesson(7, lessons))
    }

    @Test
    fun `an ended lesson is unavailable`() = runTest {
        catalog(lessonOn(3, day = 8, hour = 9))

        assertEquals(SportSignEvent.ShowLinkUnavailable, resolver.resolve(3, predicted = false))
        assertNull(resolver.linkedLesson(3, emptyList()))
    }

    @Test
    fun `a lesson missing from the catalog is unavailable`() = runTest {
        catalog(lessonOn(1, day = 9))

        assertEquals(SportSignEvent.ShowLinkUnavailable, resolver.resolve(404, predicted = false))
    }

    @Test
    fun `a linked lesson hidden by the filters is found in the full catalog with its latest state`() = runTest {
        val hidden = lessonOn(5, day = 10).copy(available = 0, canSignIn = false)
        catalog(lessonOn(1, day = 9), hidden)
        resolver.resolve(5, predicted = false)

        val refreshed = hidden.copy(available = 2, canSignIn = true)
        val lessons = listOf(lessonOn(1, day = 9), refreshed)

        assertEquals(refreshed, resolver.linkedLesson(5, lessons))
        assertNull("only the linked lesson is resolved", resolver.linkedLesson(1, lessons))
    }
}
