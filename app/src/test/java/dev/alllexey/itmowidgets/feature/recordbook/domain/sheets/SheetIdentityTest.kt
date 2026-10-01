package dev.alllexey.itmowidgets.feature.recordbook.domain.sheets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SheetIdentityTest {
    private val identity = OwnIdentity(123456, "Тестов Тест Тестович")

    @Test fun `normalisation ignores case, ё, extra spaces and the space after a dot`() {
        assertEquals("тестов т.т.", SheetText.normalize("  Тёстов  Т. Т. "))
        assertEquals(SheetText.normalize("Тестов Т.Т."), SheetText.normalize("Тестов Т. Т."))
        assertEquals("тестов тест", SheetText.normalize("Тестов Тест"))
    }

    @Test fun `tokens split on anything but letters, digits and sums`() {
        assertEquals(listOf("итого", "баллов"), SheetText.tokens("ИТОГО баллов"))
        assertEquals(listOf("σ"), SheetText.tokens("Σ"))
        assertEquals(listOf("∑", "лр"), SheetText.tokens("∑ (ЛР)"))
    }

    @Test fun `a full name gives four forms with the surname first and four with it last`() {
        assertEquals(
            setOf(
                "тестов тест тестович", "тестов тест", "тестов т.т.", "тестов т.",
                "тестович тестов тест", "тестович тестов", "тестович т.т.", "тестович т.",
            ),
            SheetIdentity.nameVariants("Тестов Тест Тестович"),
        )
    }

    @Test fun `two words give the forms without a patronymic and one word gives none`() {
        assertEquals(
            setOf("тестов тест", "тестов т.", "тест тестов", "тест т."),
            SheetIdentity.nameVariants("Тестов Тест"),
        )
        assertEquals(emptySet<String>(), SheetIdentity.nameVariants("Тестов"))
    }

    @Test fun `the ISU matches with or without spaces and nothing longer`() {
        assertEquals(KeyKind.ISU, SheetIdentity.matchOf("123 456", identity))
        assertEquals(KeyKind.ISU, SheetIdentity.matchOf("123456", identity))
        assertNull(SheetIdentity.matchOf("1234567", identity))
    }

    @Test fun `a form of the name matches and the teacher's title row does not`() {
        assertEquals(KeyKind.NAME, SheetIdentity.matchOf("Тестов Т. Т.", identity))
        assertEquals(KeyKind.NAME, SheetIdentity.matchOf("ТЁСТОВ ТЕСТ", OwnIdentity(null, "Тёстов Тест Тестович")))
        assertNull(SheetIdentity.matchOf("Преподаватель: Тестов Пётр Петрович", identity))
        assertNull(SheetIdentity.matchOf("Тестов Пётр Петрович", identity))
    }

    @Test fun `a person cell is an ISU or a short name, never a column title`() {
        assertNull(SheetIdentity.personKind("ФИО студента"))
        assertNull(SheetIdentity.personKind("ФИО"))
        assertNull(SheetIdentity.personKind("Преподаватель: Тестов Пётр Петрович"))
        assertNull(SheetIdentity.personKind("12"))
        assertNull(SheetIdentity.personKind("P3110"))
        assertEquals(KeyKind.NAME, SheetIdentity.personKind("Тестова Анна"))
        assertEquals(KeyKind.NAME, SheetIdentity.personKind("Тестов Т.Т."))
        assertEquals(KeyKind.ISU, SheetIdentity.personKind("100001"))
    }
}
