package dev.alllexey.itmowidgets.core.reviews

import org.junit.Assert.assertEquals
import org.junit.Test

class TeacherReviewLimitsTest {

    @Test
    fun `cyrillic letters count one each`() {
        assertEquals(6, TeacherReviewLimits.length("Отзыв!"))
    }

    @Test
    fun `an emoji outside the basic plane counts once`() {
        val text = "Хорошо 👍😀"
        assertEquals(9, TeacherReviewLimits.length(text))
        assertEquals(text.codePointCount(0, text.length), TeacherReviewLimits.length(text))
    }

    @Test
    fun `unpaired surrogates count once each as codePointCount does`() {
        listOf("\uD83D", "a\uDC4Db", "\uDC4D\uD83D", "\uD83Dx", "").forEach { text ->
            assertEquals(text, text.codePointCount(0, text.length), TeacherReviewLimits.length(text))
        }
    }
}
