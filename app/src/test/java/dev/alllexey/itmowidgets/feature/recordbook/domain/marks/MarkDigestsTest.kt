package dev.alllexey.itmowidgets.feature.recordbook.domain.marks

import dev.alllexey.itmowidgets.core.recordbook.BarsLoginPrompt
import dev.alllexey.itmowidgets.feature.recordbook.domain.subjectNameKey
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkDigestsTest {

    @Test
    fun `nothing is shown or marked from midnight until six, not even the prompt`() {
        val news = listOf(news("Физика", 1))

        for (time in listOf(LocalTime.MIDNIGHT, LocalTime.of(3, 0), LocalTime.of(5, 59, 59))) {
            assertEquals(
                MarkDigestDecision(null, emptySet(), showPrompt = false),
                MarkDigests.decide(news, BarsLoginPrompt.PENDING, DAY.atTime(time))
            )
        }
    }

    @Test
    fun `six o'clock is not quiet`() {
        assertNotNull(MarkDigests.decide(listOf(news("Физика", 1)), BarsLoginPrompt.NONE, DAY.atTime(6, 0)).digest)
    }

    @Test
    fun `the digest names every unread subject, newest first, and handles only the pending ones`() {
        val older = news("Химия", 1, notified = true)
        val newer = news("Физика", 2)
        val same = news("Алгебра", 2)

        val decision = MarkDigests.decide(listOf(older, newer, same), BarsLoginPrompt.NONE, DAY.atTime(12, 0))

        assertEquals(listOf("Алгебра", "Физика", "Химия"), decision.digest?.subjects)
        assertEquals(setOf(newer.id, same.id), decision.handled)
    }

    @Test
    fun `nothing pending shows no digest but the prompt may still come`() {
        val decision = MarkDigests.decide(listOf(news("Физика", 1, notified = true)), BarsLoginPrompt.PENDING, DAY.atTime(12, 0))

        assertNull(decision.digest)
        assertEquals(emptySet<String>(), decision.handled)
        assertTrue(decision.showPrompt)
    }

    @Test
    fun `single is set only for one unread subject`() {
        val one = news("Физика", 1)

        assertEquals(one, MarkDigests.decide(listOf(one), BarsLoginPrompt.NONE, DAY.atTime(12, 0)).digest?.single)
        assertNull(MarkDigests.decide(listOf(one, news("Химия", 2)), BarsLoginPrompt.NONE, DAY.atTime(12, 0)).digest?.single)
    }

    @Test
    fun `only a pending prompt is shown`() {
        assertTrue(MarkDigests.decide(emptyList(), BarsLoginPrompt.PENDING, DAY.atTime(12, 0)).showPrompt)
        assertFalse(MarkDigests.decide(emptyList(), BarsLoginPrompt.SHOWN, DAY.atTime(12, 0)).showPrompt)
        assertFalse(MarkDigests.decide(emptyList(), BarsLoginPrompt.NONE, DAY.atTime(12, 0)).showPrompt)
    }

    private companion object {
        val DAY: LocalDate = LocalDate.of(2026, 9, 7)
        val HALF = StudyHalf(2026, 1)

        fun news(name: String, minute: Long, notified: Boolean = false): MarkNews {
            val key = subjectNameKey(name)
            val at = Instant.parse("2026-09-07T09:00:00Z").plusSeconds(minute * 60)
            return MarkNews(MarkNews.idOf(HALF, key), HALF, key, name, at, notified)
        }
    }
}
