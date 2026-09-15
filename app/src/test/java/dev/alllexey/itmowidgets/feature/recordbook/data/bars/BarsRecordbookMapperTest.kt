package dev.alllexey.itmowidgets.feature.recordbook.data.bars

import api.bars.model.StudentJournal
import com.google.gson.Gson
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.BarsJournalReference
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookRate
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubjectStatus
import org.junit.Assert.*
import org.junit.Test

class BarsRecordbookMapperTest {
    private val mapper = BarsRecordbookMapper()
    private val ref = BarsJournalReference(8, "flow", "7", 2025, 2)
    private fun checkpoint(id: Long, children: String = "[]", name: String? = " Работа $id ", type: String = "Тест") =
        "{\"id\":$id,\"name\":${name?.let { "\"$it\"" }},\"type\":\"$type\",\"min_grade\":1.0,\"max_grade\":10.0,\"key\":true,\"sub_checkpoints\":$children}"
    private fun approval(attempt: Int, rate: String, invalid: Boolean = false, course: Boolean = false, absent: Boolean = false) =
        "{\"attempt\":$attempt,\"checkpoint_plan_id\":8,\"student_login\":\"123\",\"mark_string\":\"$rate\",\"is_active\":true,\"is_invalid\":$invalid,\"is_absent\":$absent,\"course\":$course}"
    private fun mark(checkpoint: Long?, value: Double, absent: Boolean = false) =
        "{\"id\":1,\"checkpoint_id\":$checkpoint,\"checkpoint_plan_id\":8,\"mark\":$value,\"is_absent\":$absent}"
    private fun journal(
        regular: String = "[${mark(6, 7.5)}]", total: String = "17.5", finalMark: String? = mark(9, 10.0),
        additional: String? = null, approvals: String = "[${approval(1, "Неуд., FX")},${approval(2, "Хор., B")}]",
        plan: String = "[${checkpoint(6)}]", owner: String = "123", year: String = "2025/2026", planId: Long = 8, courseProject: Boolean = false
    ): StudentJournal = Gson().fromJson("""{"students":[{"student_login":"$owner","marks":{"regular":$regular,"total":$total,
        ${finalMark?.let { "\"final\":$it," } ?: ""}${additional?.let { "\"additional\":$it," } ?: ""}"active_approvals":$approvals}}],
        "headers":{"plan":{"id":$planId,"year":"$year","discipline":{"id":90,"name":" Предмет "},"regular_checkpoints":$plan,
        "final_checkpoint":${checkpoint(9, name = null, type = "Экзамен")},"has_course_project":$courseProject},"type":"flow","identifier":"7"}}""", StudentJournal::class.java)

    @Test fun `maps own scores and latest valid non-course approval independent of order`() {
        val s = mapper.subject(journal(), ref, "123")
        assertEquals("Предмет", s.name)
        assertEquals(17.5, s.score!!, 0.0)
        assertEquals("4/B", s.rate)
        assertEquals(2, s.attempt)
        assertEquals(90L, s.disciplineId)
        assertEquals(ref, s.barsJournal)
        assertEquals("Экзамен", s.controlType)
        assertNull(s.examDate)
        assertEquals(s, mapper.subject(journal(approvals = "[${approval(2, "Хор., B")},${approval(1, "Неуд., FX")}]"), ref, "123"))
    }
    @Test fun `does not infer grade from total or invalid and course approvals`() {
        val j = journal(regular = "[]", total = "100.0", finalMark = null, approvals = "[${approval(1, "Отл., A", invalid = true)},${approval(2, "Отл., A", course = true)}]")
        assertNull(mapper.subject(j, ref, "123").rate)
    }
    @Test fun `untouched journal has no score instead of a zero`() {
        assertNull(mapper.subject(journal(regular = "[]", total = "0.0", finalMark = null, approvals = "[]"), ref, "123").score)
        assertEquals(0.0, mapper.subject(journal(regular = "[${mark(6, 0.0)}]", total = "0.0", finalMark = null, approvals = "[]"), ref, "123").score!!, 0.0)
    }
    @Test fun `keeps missing work distinct from real zero and exposes final checkpoint`() {
        val cs = mapper.controls(journal(regular = "[${mark(6, 0.0, absent = true)}]", total = "0.0", finalMark = null, approvals = "[]"), ref, "123")
        assertEquals(0.0, cs[0].score!!, 0.0)
        assertTrue(cs[0].absent)
        assertEquals(10.0, cs[0].maximum!!, 0.0)
        assertNull(cs[1].score)
        assertEquals("Экзамен", cs[1].name)
    }
    @Test fun `empty regular marks still render all planned work without zeros`() {
        val cs = mapper.controls(journal(regular = "[]", total = "0.0", finalMark = null, approvals = "[]"), ref, "123")
        assertEquals(2, cs.size)
        assertTrue(cs.all { it.score == null })
    }
    @Test fun `retains hierarchy without calculating aggregate totals`() {
        val cs = mapper.controls(journal(plan = "[${checkpoint(5, children = "[${checkpoint(6)}]")}]"), ref, "123")
        assertNull(cs[0].score)
        assertEquals(5L, cs[1].parentId)
        assertEquals(7.5, cs[1].score!!, 0.0)
    }
    @Test fun `rejects wrong owner plan period course projects and duplicate marks`() {
        assertThrows(IllegalArgumentException::class.java) { mapper.subject(journal(), ref, "999") }
        assertThrows(IllegalArgumentException::class.java) { mapper.subject(journal(year = "2026/2027"), ref, "123") }
        assertThrows(IllegalArgumentException::class.java) { mapper.subject(journal(planId = 99), ref, "123") }
        assertThrows(IllegalArgumentException::class.java) { mapper.subject(journal(courseProject = true), ref, "123") }
        assertThrows(IllegalArgumentException::class.java) {
            mapper.controls(journal(regular = "[${mark(6, 1.0)},${mark(6, 1.0)}]", total = "2.0", finalMark = null, approvals = "[]"), ref, "123")
        }
    }
    @Test fun `additional mark is separate from checkpoints and is not added to server total`() {
        val j = journal(regular = "[]", total = "2.0", finalMark = null, additional = mark(null, 2.0), approvals = "[]")
        val cs = mapper.controls(j, ref, "123")
        assertEquals(-8L, cs.last().id)
        assertTrue(cs.last().additional)
        assertNull(cs.last().maximum)
        assertEquals(2.0, mapper.subject(j, ref, "123").score!!, 0.0)
    }
    @Test fun `absence cannot become a passed result even with an inconsistent grade string`() {
        val subject = mapper.subject(journal(regular = "[]", total = "100.0", finalMark = null, approvals = "[${approval(1, "Отл., A", absent = true)}]"), ref, "123")
        assertTrue(subject.absent)
        assertEquals(RecordbookSubjectStatus.ATTENTION, subject.status)
        assertEquals(RecordbookRate.InProgress, subject.normalizedRate)
    }
    @Test fun `missing mandatory total is not deserialized as a successful zero`() {
        val j = Gson().fromJson("""{"students":[{"student_login":"123","marks":{"regular":[],"active_approvals":[]}}],
            "headers":{"plan":{"id":8,"year":"2025/2026","discipline":{"id":90,"name":"П"},"regular_checkpoints":[],"has_course_project":false},"type":"flow","identifier":"7"}}""", StudentJournal::class.java)
        assertNull(j.students.single().marks.total)
        assertThrows(IllegalStateException::class.java) { mapper.subject(j, ref, "123") }
    }
}
