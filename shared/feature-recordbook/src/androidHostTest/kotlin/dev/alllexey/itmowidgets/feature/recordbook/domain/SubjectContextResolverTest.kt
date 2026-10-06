package dev.alllexey.itmowidgets.feature.recordbook.domain

import dev.alllexey.itmowidgets.core.schedule.ScheduleSubject
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubject
import org.junit.Assert.assertEquals
import org.junit.Test

class SubjectContextResolverTest {
    private val resolver = SubjectContextResolver()
    private val physics = ScheduleSubject(25110, "Физика", setOf(1))
    private val algebra = ScheduleSubject(26647, "Алгебра", setOf(2))

    @Test
    fun `an exact discipline id binds silently and beats a name match elsewhere`() {
        val lookalike = ScheduleSubject(99, "Физика", setOf(9))

        assertEquals(
            SubjectContext.Bound(25110, SubjectContext.Source.EXACT),
            resolver.resolve(subject(25110, "Физика"), listOf(lookalike, physics), confirmed = null)
        )
    }

    @Test
    fun `a confirmed link wins over an exact id and is ignored once its subject is gone`() {
        assertEquals(
            SubjectContext.Bound(26647, SubjectContext.Source.CONFIRMED),
            resolver.resolve(subject(25110, "Физика"), listOf(physics, algebra), confirmed = 26647)
        )
        assertEquals(
            SubjectContext.Bound(25110, SubjectContext.Source.EXACT),
            resolver.resolve(subject(25110, "Физика"), listOf(physics), confirmed = 26647)
        )
    }

    @Test
    fun `without an id match a single name match is only proposed`() {
        val context = resolver.resolve(subject(777, "  ФИЗИКА "), listOf(physics, algebra), confirmed = null)

        assertEquals(SubjectContext.Proposed(physics), context)
    }

    @Test
    fun `names compare without case, ё and spacing differences`() {
        val candidate = ScheduleSubject(5, "Теория   вероятностей", setOf(3))

        assertEquals(
            SubjectContext.Proposed(candidate),
            resolver.resolve(subject(777, "ТЕОРИЯ ВЕРОЯТНОСТЁЙ".replace("ЁЙ", "ЕЙ")), listOf(candidate), confirmed = null)
        )
    }

    @Test
    fun `several name matches ask the user and none reports unmatched`() {
        val second = ScheduleSubject(6, "физика", setOf(4))

        assertEquals(SubjectContext.Ambiguous(listOf(physics, second)), resolver.resolve(subject(777, "Физика"), listOf(physics, second), null))
        assertEquals(SubjectContext.Unmatched, resolver.resolve(subject(777, "Химия"), listOf(physics, second), null))
    }

    @Test
    fun `physical education never looks for a schedule subject`() {
        val pe = subject(25110, "Физическая культура и спорт (элективная)")

        assertEquals(SubjectContext.NotApplicable, resolver.resolve(pe, listOf(physics), confirmed = 25110))
    }

    private fun subject(disciplineId: Long, name: String) = RecordbookSubject(
        name = name, disciplineId = disciplineId, entryId = disciplineId * 10, controlType = "Экзамен", score = null,
        rate = null, attempt = null, examDate = null, hasDetails = true, teacherName = null
    )
}
