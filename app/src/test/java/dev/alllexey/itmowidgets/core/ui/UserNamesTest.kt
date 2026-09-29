package dev.alllexey.itmowidgets.core.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class UserNamesTest {

    @Test
    fun `a full name keeps the surname and joins the initials without breaks`() {
        assertEquals("Иванов И. И.", shortPersonName("  Иванов   Иван иванович "))
        assertEquals("Иванова А.", shortPersonName("Иванова Анна"))
    }

    @Test
    fun `a single word stays as it is`() {
        assertEquals("Преподаватель", shortPersonName(" Преподаватель "))
    }
}
