package dev.alllexey.itmowidgets.feature.recordbook.domain.marks

import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.recordbook.markNews
import dev.alllexey.itmowidgets.shared.feature.recordbook.RecordbookStrings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MarkNewsTextsTest {

    private val checkpoint = markNews("Матанализ").copy(
        value = MarkValue(checkpoint = "Контрольная работа 2", mark = 9.0, max = 10.0, delta = 9.0, total = 74.0),
    )

    @Test
    fun `a BARS checkpoint names itself and its mark out of the maximum with the total`() {
        assertEquals(
            UiText.Res(RecordbookStrings.recordbook_news_title, listOf("Матанализ", UiText.LowercaseFirst(UiText.Dynamic("Контрольная работа 2")))),
            MarkNewsTexts.title(checkpoint),
        )
        assertEquals(
            UiText.Joined(
                listOf(
                    UiText.Res(RecordbookStrings.recordbook_news_mark, listOf("9", "10")),
                    UiText.LowercaseFirst(UiText.Res(RecordbookStrings.recordbook_news_total, listOf("74"))),
                ),
                ", ",
            ),
            MarkNewsTexts.details(checkpoint),
        )
        assertEquals("+9", MarkNewsTexts.delta(checkpoint.value))
        assertEquals(
            UiText.Res(
                RecordbookStrings.recordbook_news_title,
                listOf("Матанализ", UiText.LowercaseFirst(UiText.Res(RecordbookStrings.recordbook_news_mark, listOf("9", "10")))),
            ),
            MarkNewsTexts.notificationTitle(checkpoint),
        )
    }

    @Test
    fun `a total alone, a grade and news without numbers say what they can`() {
        val total = markNews("Физика").copy(value = MarkValue(delta = -3.5, total = 63.5))
        assertEquals(UiText.Dynamic("Физика"), MarkNewsTexts.title(total))
        assertEquals(UiText.Res(RecordbookStrings.recordbook_news_total, listOf("63,5")), MarkNewsTexts.details(total))
        assertEquals("−3,5", MarkNewsTexts.delta(total.value))

        val grade = markNews("Физика").copy(value = MarkValue(grade = "5", total = 91.0))
        assertEquals(
            UiText.Joined(
                listOf(
                    UiText.Res(RecordbookStrings.recordbook_news_grade, listOf("5")),
                    UiText.LowercaseFirst(UiText.Res(RecordbookStrings.recordbook_news_total, listOf("91"))),
                ),
                ", ",
            ),
            MarkNewsTexts.details(grade),
        )

        val bare = markNews("Физика")
        assertEquals(UiText.Res(RecordbookStrings.recordbook_news_without_value), MarkNewsTexts.details(bare))
        assertNull(MarkNewsTexts.notificationTitle(bare))
        assertNull(MarkNewsTexts.delta(bare.value))
    }
}
