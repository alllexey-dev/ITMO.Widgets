package dev.alllexey.itmowidgets.feature.recordbook.domain.marks

import dev.alllexey.itmowidgets.feature.recordbook.domain.model.BarsJournalReference
import dev.alllexey.itmowidgets.feature.recordbook.domain.subjectNameKey
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkNewsRulesTest {

    @Test
    fun `a My ITMO event that repeats the previous BARS plan goes, anything else stays`() {
        val event = MarkEvent(MarkSource.MY_ITMO, HALF, "Физика", MarkEventKind.MARK_CHANGED)
        val current = MyItmoMarkSnapshot(HALF, listOf(subject(1, "Физика", 17.5, "4/B")))

        assertEquals(emptyList<MarkEvent>(), MarkNewsRules.withoutEchoes(listOf(event), current, bars(plan(1, "физика", 17.5, "4B"))))
        assertEquals(listOf(event), MarkNewsRules.withoutEchoes(listOf(event), current, bars(plan(1, "Физика", 18.0, "4/B"))))
        assertEquals(
            listOf(event),
            MarkNewsRules.withoutEchoes(listOf(event), current, bars(plan(1, "Физика", 17.5, "4/B"), plan(2, "Физика", 17.5, "4/B")))
        )
        assertEquals(listOf(event), MarkNewsRules.withoutEchoes(listOf(event), current, null))
        val barsEvent = event.copy(source = MarkSource.BARS)
        assertEquals(listOf(barsEvent), MarkNewsRules.withoutEchoes(listOf(barsEvent), current, bars(plan(1, "Физика", 17.5, "4/B"))))
    }

    @Test
    fun `events of both sources make one record named by My ITMO`() {
        val events = listOf(
            MarkEvent(MarkSource.BARS, HALF, "ФИЗИКА", MarkEventKind.MARK_ADDED),
            MarkEvent(MarkSource.MY_ITMO, HALF, "Физика", MarkEventKind.MARK_CHANGED)
        )

        val merged = MarkNewsRules.merge(emptyList(), events, NOW, notify = true)

        assertEquals(1, merged.size)
        assertEquals("Физика", merged.single().name)
        assertEquals("2026/2027-1|физика", merged.single().id)
        assertEquals(NOW, merged.single().detectedAt)
        assertFalse(merged.single().notified)
    }

    @Test
    fun `a background check waits for a digest again and a seen list counts as delivered`() {
        val delivered = news("Физика", NOW - 3600.seconds, notified = true)
        val pending = news("Химия", NOW - 3600.seconds, notified = false)
        val physics = MarkEvent(MarkSource.MY_ITMO, HALF, "физика", MarkEventKind.MARK_CHANGED)
        val chemistry = MarkEvent(MarkSource.BARS, HALF, "Химия", MarkEventKind.MARK_ADDED)

        val again = MarkNewsRules.merge(listOf(delivered), listOf(physics), NOW, notify = true).single()
        assertFalse(again.notified)
        assertEquals("Физика", again.name)
        assertEquals(NOW, again.detectedAt)

        assertTrue(MarkNewsRules.merge(listOf(pending), listOf(chemistry), NOW, notify = false).single().notified)
    }

    @Test
    fun `records older than thirty days go and only the hundred newest stay`() {
        val old = news("Старый", NOW - 31.days)
        val fresh = news("Свежий", NOW - 29.days)
        assertEquals(listOf(fresh), MarkNewsRules.pruned(listOf(old, fresh), NOW))

        val many = (1..105).map { news("Тестовый предмет $it", NOW - it.seconds) }
        val kept = MarkNewsRules.pruned(many.shuffled(), NOW)
        assertEquals(100, kept.size)
        assertEquals(many.take(100), kept)
    }

    @Test
    fun `a tap target needs exactly one My ITMO subject and adds a single BARS plan only with the chip on`() {
        val physics = news("Физика", NOW)
        val myItmo = MyItmoMarkSnapshot(HALF, listOf(subject(42, "Физика", 10.0, null), subject(43, "Химия", 5.0, null)))
        val bars = bars(plan(8, "Физика", 10.0, null))

        assertEquals(
            MarkSubjectTarget(1L, 3, "2026/2027", 42L, bars = null),
            MarkNewsRules.target(physics, myItmo, bars, withBars = false)
        )
        assertEquals(
            BarsJournalReference(8L, "flow", "7", 2026, 1),
            MarkNewsRules.target(physics, myItmo, bars, withBars = true)?.bars
        )
        val twice = MyItmoMarkSnapshot(HALF, listOf(subject(42, "Физика", 10.0, null), subject(44, "физика", 1.0, null)))
        assertNull(MarkNewsRules.target(physics, twice, bars, withBars = true))
        assertNull(MarkNewsRules.target(physics, null, bars, withBars = true))
    }


    @Test
    fun `a sheet and a My ITMO event of one subject are one record named by My ITMO`() {
        val events = listOf(
            MarkEvent(MarkSource.SHEETS, HALF, "ФИЗИКА", MarkEventKind.MARK_CHANGED),
            MarkEvent(MarkSource.MY_ITMO, HALF, "Физика", MarkEventKind.MARK_ADDED)
        )

        val merged = MarkNewsRules.merge(emptyList(), events, NOW, notify = true)

        assertEquals(listOf("Физика"), merged.map { it.name })
        assertFalse(merged.single().notified)
    }

    @Test
    fun `a sheet event alone is a record with its name`() {
        val event = MarkEvent(MarkSource.SHEETS, HALF, "Тестовый предмет", MarkEventKind.MARK_ADDED)

        val merged = MarkNewsRules.merge(emptyList(), listOf(event), NOW, notify = true)

        assertEquals(listOf("Тестовый предмет"), merged.map { it.name })
        assertEquals("2026/2027-1|тестовый предмет", merged.single().id)
    }
    private companion object {
        val HALF = StudyHalf(2026, 1)
        val NOW: Instant = Instant.parse("2026-09-07T09:00:00Z")

        fun subject(entryId: Long, name: String, score: Double?, rate: String?) =
            MyItmoSubjectMark(1L, 3, entryId, entryId + 100, name, score, rate)

        fun plan(id: Long, name: String, score: Double?, rate: String?) =
            BarsPlanMarks(id, "flow", "7", name, score, rate, null, false, emptyList())

        fun bars(vararg plans: BarsPlanMarks) = BarsMarkSnapshot(HALF, plans.toList())

        fun news(name: String, detectedAt: Instant, notified: Boolean = false): MarkNews {
            val key = subjectNameKey(name)
            return MarkNews(MarkNews.idOf(HALF, key), HALF, key, name, detectedAt, notified)
        }
    }
}
