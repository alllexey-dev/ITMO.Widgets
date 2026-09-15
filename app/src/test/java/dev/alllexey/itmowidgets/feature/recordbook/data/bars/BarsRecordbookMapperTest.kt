package dev.alllexey.itmowidgets.feature.recordbook.data.bars

import com.google.gson.Gson
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.BarsJournalReference
import org.junit.Assert.*
import org.junit.Test

class BarsRecordbookMapperTest {
    private val mapper = BarsRecordbookMapper()
    private val ref = BarsJournalReference(8, "flow", "7", 2025, 2)
    private fun checkpoint(id: Long, children: List<BarsCheckpoint> = emptyList()) =
        BarsCheckpoint(id, " Работа $id ", "Тест", 1.0, 10.0, true, children)
    private fun approval(attempt: Int, rate: String) = BarsApproval(attempt, 8, "123", rate, true, false, false, false)
    private fun journal(marks: BarsMarks = BarsMarks(listOf(BarsMark(6, 8, 7.5, false)), 17.5,
        BarsMark(9, 8, 10.0, false), approvals = listOf(approval(1, "2/FX"), approval(2, "4/B")))) =
        BarsJournal(listOf(BarsStudent("123", marks)), BarsHeaders(BarsPlan(8, "2025/2026",
            BarsPlanDiscipline(90, " Предмет "), listOf(checkpoint(6)), checkpoint(9).copy(name = null, type = "Экзамен"), false), "flow", "7"))

    @Test fun `maps own scores and latest valid non-course approval independent of order`() {
        val s = mapper.subject(journal(), ref, "123")
        assertEquals("Предмет", s.name)
        assertEquals(17.5, s.score!!, 0.0)
        assertEquals("4/B", s.rate)
        assertEquals(2, s.attempt)
        assertEquals(90L, s.disciplineId)
        assertEquals(ref, s.barsJournal)
        assertNull(s.examDate)
        val reversed = journal().let { it.copy(students = listOf(it.students[0].copy(marks = it.students[0].marks.copy(approvals = it.students[0].marks.approvals.reversed())))) }
        assertEquals(s, mapper.subject(reversed, ref, "123"))
    }
    @Test fun `BARS word grades become recordbook codes and credits stay words`() {
        fun rate(text: String) = mapper.subject(journal(BarsMarks(emptyList(), 1.0, approvals = listOf(approval(1, text)))), ref, "123").rate
        assertEquals("3/E", rate("Удвл., E"))
        assertEquals("4/C", rate("Хор., C"))
        assertEquals("5/A", rate("Отл., A"))
        assertEquals("2/FX", rate("Неуд., FX"))
        assertEquals("Зачет", rate("Зачет"))
        assertEquals("Незачет", rate("Незачет"))
        assertEquals("Удвл.", rate("Удвл."))
    }
    @Test fun `does not infer grade from total or invalid and course approvals`() {
        val m = BarsMarks(emptyList(), 100.0, approvals = listOf(approval(1, "5/A").copy(invalid = true), approval(2, "5/A").copy(course = true)))
        assertNull(mapper.subject(journal(m), ref, "123").rate)
    }
    @Test fun `keeps missing work distinct from real zero and exposes final checkpoint`() {
        val m = BarsMarks(listOf(BarsMark(6, 8, 0.0, true)), 0.0, approvals = emptyList())
        val cs = mapper.controls(journal(m), ref, "123")
        assertEquals(0.0, cs[0].score!!, 0.0)
        assertTrue(cs[0].absent)
        assertEquals(10.0, cs[0].maximum!!, 0.0)
        assertNull(cs[1].score)
        assertEquals("Экзамен", cs[1].name)
    }
    @Test fun `empty regular marks still render all planned work without zeros`() {
        val cs = mapper.controls(journal(BarsMarks(emptyList(), 0.0, approvals = emptyList())), ref, "123")
        assertEquals(2, cs.size)
        assertTrue(cs.all { it.score == null })
    }
    @Test fun `retains hierarchy without calculating aggregate totals`() {
        val j = journal().let { it.copy(headers = it.headers.copy(plan = it.headers.plan.copy(regular = listOf(checkpoint(5, listOf(checkpoint(6))))))) }
        val cs = mapper.controls(j, ref, "123")
        assertNull(cs[0].score)
        assertEquals(5L, cs[1].parentId)
        assertEquals(7.5, cs[1].score!!, 0.0)
        assertEquals(17.5, mapper.subject(j, ref, "123").score!!, 0.0)
    }
    @Test fun `rejects wrong owner plan period and duplicate marks`() {
        assertThrows(IllegalArgumentException::class.java) { mapper.subject(journal(), ref, "999") }
        assertThrows(IllegalArgumentException::class.java) { mapper.subject(journal(), ref.copy(yearStart = 2026), "123") }
        assertThrows(IllegalArgumentException::class.java) { mapper.subject(journal(), ref.copy(planId = 99), "123") }
        val m = BarsMarks(List(2) { BarsMark(6, 8, 1.0, false) }, 1.0, approvals = emptyList())
        assertThrows(IllegalArgumentException::class.java) { mapper.controls(journal(m), ref, "123") }
    }
    @Test fun `additional mark is separate from checkpoints and is not added to server total`() {
        val m = BarsMarks(emptyList(), 2.0, additional = BarsMark(null, 8, 2.0, false), approvals = emptyList())
        val cs = mapper.controls(journal(m), ref, "123")
        assertEquals(-8L, cs.last().id)
        assertNull(cs.last().maximum)
        assertEquals(2.0, mapper.subject(journal(m), ref, "123").score!!, 0.0)
    }
    @Test fun `absence cannot become a passed result even with an inconsistent grade string`() {
        val m = BarsMarks(emptyList(), 100.0, approvals = listOf(approval(1, "5/A").copy(absent = true)))
        val subject = mapper.subject(journal(m), ref, "123")
        assertTrue(subject.absent)
        assertEquals(dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubjectStatus.ATTENTION, subject.status)
        assertEquals(dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookRate.InProgress, subject.normalizedRate)
    }

    @Test fun `missing mandatory total is not deserialized as a successful zero`() {
        val m = Gson().fromJson("{\"regular\":[],\"active_approvals\":[]}", BarsMarks::class.java)
        assertNull(m.total)
        assertThrows(IllegalStateException::class.java) { mapper.subject(journal(m), ref, "123") }
    }
}
