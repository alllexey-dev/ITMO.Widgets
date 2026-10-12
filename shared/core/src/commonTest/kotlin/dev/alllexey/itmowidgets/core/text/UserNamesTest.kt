package dev.alllexey.itmowidgets.core.text

import dev.alllexey.itmowidgets.shared.core.CoreStrings
import kotlin.test.Test
import kotlin.test.assertEquals

class UserNamesTest {

    @Test
    fun aPublishedNameIsShownTrimmed() {
        assertEquals(UiText.Dynamic("Анна Иванова"), userDisplayName("  Анна Иванова ", 123456))
    }

    @Test
    fun anEmptyNameFallsBackToTheIsu() {
        assertEquals(UiText.Res(CoreStrings.user_name_placeholder, listOf(123456)), userDisplayName(" ", 123456))
    }

    @Test
    fun aPersonNameShortensToInitials() {
        assertEquals("Иванов И. И.", shortPersonName("  Иванов   Иван иванович "))
        assertEquals("Иванова А.", shortPersonName("Иванова Анна"))
        assertEquals("Преподаватель", shortPersonName(" Преподаватель "))
    }
}
