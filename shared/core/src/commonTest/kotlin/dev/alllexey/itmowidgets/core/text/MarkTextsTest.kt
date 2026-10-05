package dev.alllexey.itmowidgets.core.text

import dev.alllexey.itmowidgets.shared.core.Res
import dev.alllexey.itmowidgets.shared.core.marks_subjects_more
import kotlin.test.Test
import kotlin.test.assertEquals

class MarkTextsTest {

    @Test
    fun upToThreeSubjectsAreNamed() {
        assertEquals(UiText.Dynamic("Физика"), markSubjectList(listOf("Физика")))
        assertEquals(UiText.Dynamic("Физика, Химия, Биология"), markSubjectList(listOf("Физика", "Химия", "Биология")))
    }

    @Test
    fun moreSubjectsAreCounted() {
        assertEquals(
            UiText.Res(Res.string.marks_subjects_more, listOf("Физика, Химия, Биология", 2)),
            markSubjectList(listOf("Физика", "Химия", "Биология", "История", "Философия"))
        )
    }
}
