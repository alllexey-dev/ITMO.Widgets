package dev.alllexey.itmowidgets.feature.recordbook.presentation

import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.schedule.ScheduleSubject
import dev.alllexey.itmowidgets.core.testing.FakeScheduleRefreshGateway
import dev.alllexey.itmowidgets.core.testing.FakeSubjectLessonsGateway
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.core.testing.subjectLesson
import dev.alllexey.itmowidgets.feature.recordbook.FakeSubjectBindingStore
import dev.alllexey.itmowidgets.feature.recordbook.domain.SubjectContext
import dev.alllexey.itmowidgets.feature.recordbook.domain.SubjectContextResolver
import dev.alllexey.itmowidgets.feature.recordbook.recordbookSubject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SubjectLessonsLoaderTest {
    private val lessons = FakeSubjectLessonsGateway()
    private val scheduleRefresh = FakeScheduleRefreshGateway()
    private val bindings = FakeSubjectBindingStore()
    private val loader = SubjectLessonsLoader(lessons, scheduleRefresh, bindings, SubjectContextResolver(),
        FixedAcademicTime(LocalDate(2026, 9, 7)))
    private val rejected = MutableStateFlow(false)
    private val fallback = listOf(SubjectTeacher("Тестовый преподаватель", null, emptyList()))

    private fun TestScope.observe(): List<SubjectLessonsUpdate> = mutableListOf<SubjectLessonsUpdate>().also { updates ->
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            loader.observe(recordbookSubject(), rejected, fallback).collect(updates::add)
        }
    }

    @Test fun `the own schedule is refreshed once for the 28 days from today`() = runTest {
        observe(); advanceUntilIdle()
        assertEquals(listOf(LocalDate(2026, 9, 7) to LocalDate(2026, 10, 5)), scheduleRefresh.requests)
        assertEquals(scheduleRefresh.requests, lessons.observedWindows)
    }

    @Test fun `an exact match shows its lessons with the teachers they name`() = runTest {
        lessons.lessons.value = listOf(subjectLesson(1, "2026-09-08", subjectId = 1L, typeId = 3, teacherFio = "Практик П. П."))
        val updates = observe(); advanceUntilIdle()
        assertEquals(SubjectLessonsState.Content(lessons.lessons.value, SubjectContext.Source.EXACT), updates.last().lessons)
        assertEquals(listOf(SubjectTeacher("Практик П. П.", 300001, listOf(3))), updates.last().teachers)
    }

    @Test fun `a binding written through the loader matches the lessons again`() = runTest {
        lessons.lessons.value = listOf(subjectLesson(1, "2026-09-08", subjectId = 555L, name = "Тестовый  ПРЕДМЕТ", teacherFio = "Иванов И. И."))
        val updates = observe(); advanceUntilIdle()
        assertEquals(SubjectLessonsState.Proposed(ScheduleSubject(555L, "Тестовый  ПРЕДМЕТ", setOf(10L))), updates.last().lessons)
        assertEquals(fallback, updates.last().teachers)

        loader.bind(1L, 555L); advanceUntilIdle()
        assertEquals(mapOf(1L to 555L), bindings.bindings)
        assertEquals(SubjectLessonsState.Content(lessons.lessons.value, SubjectContext.Source.CONFIRMED), updates.last().lessons)
        assertEquals(listOf(SubjectTeacher("Иванов И. И.", 300001, listOf(1))), updates.last().teachers)
    }

    @Test fun `a rejected proposal is unmatched and stores nothing`() = runTest {
        lessons.lessons.value = listOf(subjectLesson(1, "2026-09-08", subjectId = 555L, name = "Тестовый  ПРЕДМЕТ"))
        val updates = observe(); advanceUntilIdle()
        rejected.value = true; advanceUntilIdle()
        assertEquals(SubjectLessonsState.Unmatched, updates.last().lessons)
        assertTrue(bindings.bindings.isEmpty())
    }

    @Test fun `a failed refresh without stored lessons is an error that keeps the shown teachers`() = runTest {
        scheduleRefresh.result = AppResult.Failure(AppError.Network)
        val updates = observe(); advanceUntilIdle()
        assertEquals(SubjectLessonsState.Error(AppError.Network), updates.last().lessons)
        assertNull(updates.last().teachers)

        lessons.lessons.value = listOf(subjectLesson(1, "2026-09-08", subjectId = 1L))
        advanceUntilIdle()
        assertEquals(SubjectLessonsState.Content(lessons.lessons.value, SubjectContext.Source.EXACT), updates.last().lessons)
    }
}
