package dev.alllexey.itmowidgets.feature.recordbook.data.demo

import dev.alllexey.itmowidgets.core.demo.DemoStudy
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.testing.FakeDemoMode
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.core.testing.unreachableMyItmo
import dev.alllexey.itmowidgets.feature.recordbook.data.RecordbookRepositoryImpl
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordbookDemoGateTest {

    @Test
    fun `the program, subjects and control points come from the demo set`() = runTest {
        val repository = RecordbookRepositoryImpl(unreachableMyItmo(), FixedAcademicTime(), FakeDemoMode(active = true))

        val program = (repository.getPrograms() as AppResult.Success).value.single()
        val current = program.periods.single { it.actual }
        val subjects = (repository.getSubjects(program.id, current.semester) as AppResult.Success).value
        val closed = (repository.getSubjects(program.id, 1) as AppResult.Success).value
        val algorithms = subjects.single { it.disciplineId == DemoStudy.ALGORITHMS.id }
        val controls = (repository.getControls(algorithms.entryId) as AppResult.Success).value

        assertEquals(3, current.semester)
        assertEquals(DemoStudy.CURRENT.map { it.id }, subjects.map { it.disciplineId })
        assertTrue(closed.all { !it.rate.isNullOrBlank() })
        assertTrue(controls.any { it.parentId != null })
        assertEquals(algorithms.score!!, controls.filter { it.parentId == null }.sumOf { it.score ?: 0.0 }, 0.001)
    }
}
