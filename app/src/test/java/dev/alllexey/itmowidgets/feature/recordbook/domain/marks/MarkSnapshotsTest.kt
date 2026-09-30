package dev.alllexey.itmowidgets.feature.recordbook.domain.marks

import dev.alllexey.itmowidgets.feature.recordbook.barsJournal
import dev.alllexey.itmowidgets.feature.recordbook.barsSubject
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookControl
import dev.alllexey.itmowidgets.feature.recordbook.recordbookSubject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkSnapshotsTest {

    @Test
    fun `no score and zero are equal and hundredths decide`() {
        assertTrue(sameScore(null, null))
        assertTrue(sameScore(null, 0.0))
        assertTrue(sameScore(17.5, 17.504))
        assertFalse(sameScore(17.5, 17.6))
        assertFalse(sameScore(null, 12.5))
    }

    @Test
    fun `grades compare without case, ё, spaces and slashes`() {
        assertEquals(rateKey("4/C"), rateKey(" 4C "))
        assertEquals(rateKey("Зачёт"), rateKey("зачет"))
        assertNull(rateKey(""))
        assertNull(rateKey("  "))
        assertNull(rateKey(null))
    }

    @Test
    fun `a plan keeps graded and missed checkpoints, nested ones and the additional points`() {
        val journal = barsSubject().copy(barsJournal = barsJournal(8L))
        val controls = listOf(
            control(1, score = 10.0),
            control(2, score = null),
            control(3, score = null, absent = true),
            control(4, score = 5.0, parent = 1),
            control(-8, score = 2.0, additional = true)
        )

        val plan = BarsPlanMarks.of(journal, controls)!!

        assertEquals(8L, plan.planId)
        assertEquals("flow", plan.type)
        assertEquals("7", plan.identifier)
        assertEquals(journal.name, plan.name)
        assertEquals(journal.score, plan.score)
        assertEquals(journal.rate, plan.rate)
        assertEquals(
            listOf(
                BarsCheckpointMark(1, 10.0, false),
                BarsCheckpointMark(3, null, true),
                BarsCheckpointMark(4, 5.0, false),
                BarsCheckpointMark(-8, 2.0, false)
            ),
            plan.marks
        )
    }

    @Test
    fun `a subject without a journal is no plan`() {
        assertNull(BarsPlanMarks.of(recordbookSubject(), listOf(control(1, score = 10.0))))
    }

    @Test
    fun `a My ITMO row keeps its program, semester and identities`() {
        val subject = recordbookSubject(id = 42L).copy(score = 12.5, rate = "4/C")

        assertEquals(
            MyItmoSubjectMark(1L, 3, 42L, subject.disciplineId, subject.name, 12.5, "4/C"),
            subject.toMyItmoMark(programId = 1L, semester = 3)
        )
    }

    private fun control(id: Long, score: Double?, absent: Boolean = false, parent: Long? = null, additional: Boolean = false) =
        RecordbookControl(id, "Точка $id", score, null, null, false, null, null, parent, absent, additional)
}
