package dev.alllexey.itmowidgets.feature.recordbook.domain.lms

import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookControl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LmsMatchingTest {
    @Test fun `course requires a unique subject and school year`() {
        val current = LmsCourseScores("Веб-программирование", "Веб-программирование 26/27", emptyList())
        val old = LmsCourseScores("Веб-программирование", "Веб-программирование 25/26", emptyList())
        val other = LmsCourseScores("Программирование 2 семестр", "Программирование 26/27", emptyList())
        assertEquals(current, LmsMatching.course(listOf(old, current, other), "Веб-программирование", "2026/2027", 3))
        assertNull(LmsMatching.course(listOf(current, current), "Веб-программирование", "2026/2027", 3))
        assertNull(LmsMatching.course(listOf(old), "Веб-программирование", "2026/2027", 3))
        assertNull(LmsMatching.course(listOf(other), "Программирование", "2026/2027", 3))
    }

    @Test fun `only a confirmed unambiguous RK with matching maximum fills an empty control`() {
        val control = RecordbookControl(1, "Контрольная работа №1", null, 6.0, 10.0, true, null, null)
        val confirmed = LmsScore("РК №1", 9.0, 10.0, "VERIFIED")
        assertEquals(mapOf(1L to 9.0), LmsMatching.controls(listOf(control), listOf(confirmed)))
        assertEquals(emptyMap<Long, Double>(), LmsMatching.controls(listOf(control), listOf(confirmed.copy(verificationStatus = "WAITING_FOR_VERIFICATION"))))
        assertEquals(emptyMap<Long, Double>(), LmsMatching.controls(listOf(control), listOf(confirmed.copy(max = 20.0))))
        assertEquals(emptyMap<Long, Double>(), LmsMatching.controls(listOf(control.copy(score = 7.0)), listOf(confirmed)))
        assertEquals(emptyMap<Long, Double>(), LmsMatching.controls(listOf(control), listOf(confirmed, confirmed)))
    }
}
