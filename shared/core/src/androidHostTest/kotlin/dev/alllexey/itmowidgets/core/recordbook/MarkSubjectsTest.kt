package dev.alllexey.itmowidgets.core.recordbook

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkSubjectsTest {

    @Test
    fun `up to three names are shown and the rest is a number`() {
        val names = (1..5).map { "Тестовый предмет $it" }

        assertEquals(SubjectList(names.take(1), 0), MarkSubjects.split(names.take(1)))
        assertEquals(SubjectList(names.take(3), 0), MarkSubjects.split(names.take(3)))
        assertEquals(SubjectList(names.take(3), 2), MarkSubjects.split(names))
    }

    @Test
    fun `the order is kept and an empty list stays empty`() {
        assertEquals(listOf("В", "А", "Б"), MarkSubjects.split(listOf("В", "А", "Б", "Г")).shown)
        assertEquals(SubjectList(emptyList(), 0), MarkSubjects.split(emptyList()))
    }
}
