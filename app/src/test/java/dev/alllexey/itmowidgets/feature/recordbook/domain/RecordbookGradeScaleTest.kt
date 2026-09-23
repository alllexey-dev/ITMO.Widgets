package dev.alllexey.itmowidgets.feature.recordbook.domain

import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookAssessmentKind
import org.junit.Assert.*
import org.junit.Test

class RecordbookGradeScaleTest {
    @Test fun `grade boundaries follow the ITMO scale`() {
        val expected = listOf(
            59.0 to null, 60.0 to "3E", 67.0 to "3E", 68.0 to "3D", 74.0 to "3D", 75.0 to "4C",
            83.0 to "4C", 84.0 to "4B", 90.0 to "4B", 91.0 to "5A", 100.0 to "5A"
        )
        expected.forEach { (score, grade) -> assertEquals("score $score", grade, RecordbookGradeScale.gradeFor(score)) }
    }

    @Test fun `fractional score above a strict threshold reaches the grade`() {
        assertEquals("3D", RecordbookGradeScale.gradeFor(67.5))
        assertEquals("5A", RecordbookGradeScale.gradeFor(90.5))
        assertNull(RecordbookGradeScale.gradeFor(59.5))
    }

    @Test fun `exam hint aims at the next grade in whole points`() {
        val step = RecordbookGradeScale.nextStep(72.0, RecordbookAssessmentKind.EXAM)!!
        assertEquals("4C", step.target)
        assertEquals(3.0, step.remaining, 1e-9)
        val fractional = RecordbookGradeScale.nextStep(67.5, RecordbookAssessmentKind.EXAM)!!
        assertEquals("4C", fractional.target)
        assertEquals(7.5, fractional.remaining, 1e-9)
    }

    @Test fun `below pass the hint aims at 3E itself`() {
        val step = RecordbookGradeScale.nextStep(55.0, RecordbookAssessmentKind.EXAM)!!
        assertEquals("3E", step.target)
        assertEquals(5.0, step.remaining, 1e-9)
    }

    @Test fun `graded credit uses the full scale`() {
        val step = RecordbookGradeScale.nextStep(90.0, RecordbookAssessmentKind.GRADED_CREDIT)!!
        assertEquals("5A", step.target)
        assertEquals(1.0, step.remaining, 1e-9)
        assertEquals("3D", RecordbookGradeScale.nextStep(60.0, RecordbookAssessmentKind.OTHER)!!.target)
    }

    @Test fun `plain credit has only the pass threshold`() {
        val step = RecordbookGradeScale.nextStep(52.0, RecordbookAssessmentKind.CREDIT)!!
        assertEquals(RecordbookGradeScale.CREDIT_TARGET, step.target)
        assertEquals(8.0, step.remaining, 1e-9)
        assertNull(RecordbookGradeScale.nextStep(60.0, RecordbookAssessmentKind.CREDIT))
        assertNull(RecordbookGradeScale.nextStep(75.0, RecordbookAssessmentKind.CREDIT))
    }

    @Test fun `no hint without a score or at the top grade`() {
        RecordbookAssessmentKind.entries.forEach { assertNull(RecordbookGradeScale.nextStep(null, it)) }
        assertNull(RecordbookGradeScale.nextStep(91.0, RecordbookAssessmentKind.EXAM))
        assertNull(RecordbookGradeScale.nextStep(95.5, RecordbookAssessmentKind.GRADED_CREDIT))
    }
}
