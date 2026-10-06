package dev.alllexey.itmowidgets.feature.recordbook.presentation

import dev.alllexey.itmowidgets.core.reviews.TeacherLevel
import dev.alllexey.itmowidgets.core.testing.FakeTeacherLevelsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SubjectTeacherLevelsLoaderTest {
    private val repository = FakeTeacherLevelsRepository()
    private val loader = SubjectTeacherLevelsLoader(repository)

    @Test fun `only teachers whose ISU opens a profile are asked about`() {
        val teachers = listOf(
            SubjectTeacher("Лектор Л. Л.", 123456, listOf(1)),
            SubjectTeacher("Без ИСУ Б. Б.", null, emptyList()),
            SubjectTeacher("Нулевой Н. Н.", 0, emptyList()),
            SubjectTeacher("Большой Б. Б.", Int.MAX_VALUE + 1L, emptyList()),
        )
        assertEquals(setOf(123456), loader.isusOf(teachers))
    }

    @Test fun `tones come keyed by the ISU the page shows and a teacher without one is absent`() = runTest {
        repository.levels[123456] = TeacherLevel.POSITIVE
        assertEquals(mapOf(123456L to TeacherLevel.POSITIVE), loader.levels(setOf(123456, 234567)))
        assertEquals(listOf(setOf(123456, 234567)), repository.calls)
    }
}
