package dev.alllexey.itmowidgets.feature.recordbook.domain

import dev.alllexey.itmowidgets.feature.recordbook.barsSubject
import dev.alllexey.itmowidgets.feature.recordbook.recordbookSubject
import org.junit.Assert.*
import org.junit.Test

class RecordbookBarsMergeTest {
    @Test fun `matches by normalized name and keeps MyITMO identity`() {
        val merged = RecordbookBarsMerge.apply(listOf(recordbookSubject(name = "Тестовый  предмет ")), listOf(barsSubject(name = "тестовый предмет")))
        val subject = merged.single()
        assertEquals(42L, subject.entryId)
        assertEquals(1L, subject.disciplineId)
        assertEquals(91.5, subject.score!!, 0.0)
        assertEquals("5/A", subject.rate)
        assertTrue(subject.hasDetails)
        assertEquals(barsSubject().barsJournal, subject.barsJournal)
    }
    @Test fun `yo and e are the same letter for matching`() {
        val merged = RecordbookBarsMerge.apply(listOf(recordbookSubject(name = "Зачёт по чему-то")), listOf(barsSubject(name = "Зачет по чему-то")))
        assertNotNull(merged.single().barsJournal)
    }
    @Test fun `ambiguous names on either side are left untouched`() {
        val twoBars = RecordbookBarsMerge.apply(listOf(recordbookSubject()), listOf(barsSubject(), barsSubject(score = 1.0)))
        assertNull(twoBars.single().barsJournal)
        val twoMine = RecordbookBarsMerge.apply(listOf(recordbookSubject(id = 1), recordbookSubject(id = 2)), listOf(barsSubject()))
        assertTrue(twoMine.all { it.barsJournal == null })
    }
    @Test fun `BARS values replace score and rate even when empty`() {
        val merged = RecordbookBarsMerge.apply(listOf(recordbookSubject()), listOf(barsSubject(score = null, rate = null)))
        assertNull(merged.single().score)
        assertNull(merged.single().rate)
    }
}
