package dev.alllexey.itmowidgets.core.text

import dev.alllexey.itmowidgets.shared.core.Res
import dev.alllexey.itmowidgets.shared.core.user_name_placeholder
import kotlin.test.Test
import kotlin.test.assertEquals

class UserNamesTest {

    @Test
    fun aPublishedNameIsShownTrimmed() {
        assertEquals(UiText.Dynamic("Анна Иванова"), userDisplayName("  Анна Иванова ", 123456))
    }

    @Test
    fun anEmptyNameFallsBackToTheIsu() {
        assertEquals(UiText.Res(Res.string.user_name_placeholder, listOf(123456)), userDisplayName(" ", 123456))
    }

    @Test
    fun aPersonNameShortensToInitials() {
        assertEquals("Иванов И. И.", shortPersonName("  Иванов   Иван иванович "))
        assertEquals("Иванова А.", shortPersonName("Иванова Анна"))
        assertEquals("Преподаватель", shortPersonName(" Преподаватель "))
    }
}
