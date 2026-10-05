package dev.alllexey.itmowidgets.feature.schedule.domain.changes

import dev.alllexey.itmowidgets.core.schedule.LessonSlot
import dev.alllexey.itmowidgets.core.schedule.ScheduleChange
import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeField
import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeKind
import java.time.LocalDate
import java.time.LocalTime
import kotlin.time.Instant
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.datetime.toKotlinLocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleChangeDigestTest {

    @Test
    fun `nothing is shown or marked from midnight until six`() {
        val changes = listOf(moved("1", TUE, WED), moved("2", MON, MON))

        for (time in listOf(LocalTime.MIDNIGHT, LocalTime.of(3, 0), LocalTime.of(5, 59, 59))) {
            assertEquals(DigestDecision(null, emptySet()), ScheduleChangeDigests.decide(changes, TUE.atTime(time)))
        }
    }

    @Test
    fun `six o'clock and late evening are not quiet`() {
        val changes = listOf(moved("1", WED, THU))

        assertNotNull(ScheduleChangeDigests.decide(changes, TUE.atTime(6, 0)).digest)
        assertNotNull(ScheduleChangeDigests.decide(changes, TUE.atTime(23, 59)).digest)
    }

    @Test
    fun `a change found at night is delivered after six and today and tomorrow count from the delivery`() {
        val nightly = moved("1", THU, WED)

        assertNull(ScheduleChangeDigests.decide(listOf(nightly), TUE.atTime(1, 0)).digest)
        val decision = ScheduleChangeDigests.decide(listOf(nightly), TUE.atTime(6, 10))

        assertEquals(nightly, decision.digest?.first)
        assertTrue(decision.digest!!.audible)
        assertEquals(setOf("1"), decision.handled)
    }

    @Test
    fun `pending changes over by the delivery are handled without being shown`() {
        val over = moved("1", MON, MON)
        val fresh = moved("2", THU, FRI)

        val mixed = ScheduleChangeDigests.decide(listOf(over, fresh), MON.atTime(12, 0))
        assertEquals(fresh, mixed.digest?.first)
        assertEquals(1, mixed.digest?.unread)
        assertEquals(setOf("1", "2"), mixed.handled)

        val onlyOver = ScheduleChangeDigests.decide(listOf(over, moved("3", MON, MON)), MON.atTime(12, 0))
        assertEquals(DigestDecision(null, setOf("1", "3")), onlyOver)
    }

    @Test
    fun `a change touching today or tomorrow makes a sound and a later one does not`() {
        val now = TUE.atTime(12, 0)

        assertTrue(ScheduleChangeDigests.decide(listOf(moved("1", TUE, FRI, start = LocalTime.of(15, 0))), now).digest!!.audible)
        assertTrue(ScheduleChangeDigests.decide(listOf(moved("1", FRI, WED)), now).digest!!.audible)
        assertFalse(ScheduleChangeDigests.decide(listOf(moved("1", THU, FRI)), now).digest!!.audible)
    }

    @Test
    fun `the digest names the soonest fresh change and counts every unread one that is not over`() {
        val later = moved("1", FRI, FRI.plusDays(1))
        val soonest = moved("2", THU, FRI)
        val deliveredBefore = moved("3", WED, THU, notified = true)
        val read = moved("4", WED, WED, read = true)
        val over = moved("5", MON, MON, notified = true)

        val digest = ScheduleChangeDigests.decide(listOf(later, soonest, deliveredBefore, read, over), MON.atTime(12, 0)).digest!!

        assertEquals(soonest, digest.first)
        assertEquals(3, digest.unread)
    }

    @Test
    fun `read and delivered changes are not pending`() {
        val decision = ScheduleChangeDigests.decide(
            listOf(moved("1", WED, THU, read = true), moved("2", WED, THU, notified = true)),
            MON.atTime(12, 0)
        )

        assertEquals(DigestDecision(null, emptySet()), decision)
    }

    private fun moved(
        id: String,
        from: LocalDate,
        to: LocalDate,
        start: LocalTime = LocalTime.of(8, 20),
        read: Boolean = false,
        notified: Boolean = false
    ) = ScheduleChange(
        id = id, detectedAt = Instant.parse("2026-09-07T09:00:00Z"), kind = ScheduleChangeKind.UPDATED,
        fields = setOf(ScheduleChangeField.TIME), subjectName = "Предмет $id", typeId = 1, flowName = null,
        before = slot(id.toLong(), from, LocalTime.of(8, 20)), after = slot(id.toLong(), to, start),
        read = read, notified = notified
    )

    private fun slot(pairId: Long, date: LocalDate, start: LocalTime) = LessonSlot(
        pairId = pairId, date = date.toKotlinLocalDate(), start = start.toKotlinLocalTime(),
        end = start.plusMinutes(90).toKotlinLocalTime(), room = "1506", building = null,
        formatId = 1, format = "Очный", teacherIsu = null, teacherName = null
    )

    private companion object {
        val MON: LocalDate = LocalDate.of(2026, 9, 7)
        val TUE: LocalDate = LocalDate.of(2026, 9, 8)
        val WED: LocalDate = LocalDate.of(2026, 9, 9)
        val THU: LocalDate = LocalDate.of(2026, 9, 10)
        val FRI: LocalDate = LocalDate.of(2026, 9, 11)
    }
}
