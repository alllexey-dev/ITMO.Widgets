package dev.alllexey.itmowidgets.feature.recordbook.domain

import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookControl
import org.junit.Assert.*
import org.junit.Test

class RecordbookControlGroupsTest {
    private fun control(
        id: Long,
        name: String,
        score: Double? = null,
        minimum: Double? = null,
        maximum: Double? = null,
        parent: Long? = null
    ) = RecordbookControl(id, name, score, minimum, maximum, false, null, null, parent)

    @Test fun `numbered top-level controls form a group titled by the dictionary`() {
        val labs = listOf(control(1, "Лабораторная работа №1"), control(2, "Лабораторная работа № 2"), control(3, "лабораторная  работа 3"))
        val entries = RecordbookControlGroups.groupControls(labs + control(4, "Контрольная работа №1") + control(5, "Контрольная работа №2"))
        assertEquals(listOf("Лабораторные", "Контрольные"), entries.map { (it as ControlGroup).title })
        assertEquals(listOf(1L, 2L, 3L), (entries.first() as ControlGroup).controls.map { it.id })
    }

    @Test fun `unknown repeated names keep their own title without the number`() {
        val entries = RecordbookControlGroups.groupControls(listOf(control(1, "Тест 1"), control(2, "Тест 2")))
        assertEquals("Тест", (entries.single() as ControlGroup).title)
    }

    @Test fun `tree node with children becomes a group of its leaves`() {
        val controls = listOf(
            control(1, "Текущий контроль", score = 40.0, maximum = 60.0),
            control(2, "Опрос", score = 10.0, maximum = 20.0, parent = 1),
            control(3, "Модуль", score = 30.0, maximum = 40.0, parent = 1),
            control(4, "Задача", score = 30.0, maximum = 40.0, parent = 3),
            control(5, "Экзамен", maximum = 40.0)
        )
        val entries = RecordbookControlGroups.groupControls(controls)
        val group = entries.first() as ControlGroup
        assertEquals("Текущий контроль", group.title)
        assertEquals(listOf(2L, 4L), group.controls.map { it.id })
        assertEquals(40.0, group.score!!, 0.0)
        assertEquals(60.0, group.maximum!!, 0.0)
        assertEquals(ControlEntry.Single(controls.last()), entries.last())
    }

    @Test fun `single controls stay separate rows in server order`() {
        val controls = listOf(control(1, "Экзамен"), control(2, "Лабораторная работа №1"), control(3, "Реферат"))
        assertEquals(controls.map { ControlEntry.Single(it) }, RecordbookControlGroups.groupControls(controls))
    }

    @Test fun `group takes the place of its first member`() {
        val controls = listOf(control(1, "ДЗ 1"), control(2, "Реферат"), control(3, "ДЗ 2"))
        val entries = RecordbookControlGroups.groupControls(controls)
        assertEquals(listOf(1L, 3L), (entries[0] as ControlGroup).controls.map { it.id })
        assertEquals(ControlEntry.Single(controls[1]), entries[1])
        assertEquals(2, entries.size)
    }

    @Test fun `sum counts only known scores and stays null without any`() {
        val graded = RecordbookControlGroups.groupControls(listOf(
            control(1, "Лабораторная работа 1", score = 8.0, maximum = 10.0),
            control(2, "Лабораторная работа 2", maximum = 10.0),
            control(3, "Лабораторная работа 3", score = 4.5, maximum = 10.0)
        )).single() as ControlGroup
        assertEquals(12.5, graded.score!!, 0.0)
        assertEquals(30.0, graded.maximum!!, 0.0)
        val ungraded = RecordbookControlGroups.groupControls(listOf(control(1, "Лабораторная работа 1"), control(2, "Лабораторная работа 2")))
            .single() as ControlGroup
        assertNull(ungraded.score)
        assertNull(ungraded.maximum)
    }

    @Test fun `below minimum lists graded controls under their minimum`() {
        val low = control(1, "Лабораторная работа 1", score = 2.0, minimum = 5.0)
        val group = RecordbookControlGroups.groupControls(listOf(
            low,
            control(2, "Лабораторная работа 2", score = 5.0, minimum = 5.0),
            control(3, "Лабораторная работа 3", minimum = 5.0),
            control(4, "Лабораторная работа 4", score = 0.0, minimum = 0.0)
        )).single() as ControlGroup
        assertEquals(listOf(low), group.belowMinimum)
    }
}
