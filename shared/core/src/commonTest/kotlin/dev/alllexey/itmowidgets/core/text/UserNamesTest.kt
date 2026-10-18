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

    @Test
    fun avatarInitialsAreTheSurnameAndTheGivenName() {
        assertEquals("ПА", personInitials("  Петров   Алексей иванович "))
        assertEquals("ИА", personInitials("иванова Анна"))
        assertEquals("П", personInitials(" Преподаватель "))
        assertEquals("SJ", personInitials("smith John"))
        assertEquals("РН", personInitials("Римский-Корсаков Николай Андреевич"))
        assertEquals("ЁЁ", personInitials("ёлкин Ёжик"))
    }

    @Test
    fun avatarInitialsOfNoNameAreAQuestionMark() {
        assertEquals("?", personInitials(""))
        assertEquals("?", personInitials("  "))
    }

    @Test
    fun theStructuredNameIsTheSurnameThenTheGivenName() {
        assertEquals("Петров Алексей", personName(" Петров ", "Алексей"))
        assertEquals("Ёлкина-Петрова Анна", personName("Ёлкина-Петрова", "Анна"))
    }

    @Test
    fun avatarInitialsPreferTheStructuredParts() {
        assertEquals("ДМ", personInitials("Де Ла Круз Мария", " де Ла Круз ", "Мария"))
        assertEquals("ЁА", personInitials("Анна Ёлкина", "ёлкина", "Анна"))
        assertEquals("ИА", personInitials("Иванова Анна", "Иванова", null))
        assertEquals("ИА", personInitials("Иванова Анна", " ", "Анна"))
        assertEquals("?", personInitials("", null, null))
    }

    @Test
    fun theStructuredNameNeedsBothParts() {
        assertEquals(null, personName("Петров", null))
        assertEquals(null, personName(" ", "Алексей"))
        assertEquals(null, personName(null, null))
    }

    @Test
    fun theGivenNameComesFromItmoIdFirst() {
        assertEquals("Мария", personGivenName("Де Ла Круз Мария", " Мария "))
        assertEquals("Иван", personGivenName("Кузнецов Иван", null))
        assertEquals("Иван", personGivenName("  Кузнецов   Иван Петрович ", " "))
        assertEquals("Преподаватель", personGivenName(" Преподаватель ", null))
        assertEquals("", personGivenName("  ", null))
    }
}
