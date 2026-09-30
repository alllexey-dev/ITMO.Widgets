package dev.alllexey.itmowidgets.feature.recordbook.domain.marks

import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarkEventKind.FINAL_CHANGED
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarkEventKind.MARK_ADDED
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarkEventKind.MARK_CHANGED
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkDiffTest {

    @Test
    fun `no previous snapshot or another half-year is only a baseline`() {
        val current = myItmo(subject(1, score = 12.5))

        val first = MarkDiff.myItmo(null, current)
        val otherHalf = MarkDiff.myItmo(MyItmoMarkSnapshot(StudyHalf(2025, 2), listOf(subject(1, score = 1.0))), current)
        val bars = MarkDiff.bars(BarsMarkSnapshot(StudyHalf(2025, 2), listOf(plan(1, mark(1, 1.0)))), barsOf(plan(1, mark(1, 2.0))))

        listOf(first, otherHalf).forEach {
            assertTrue(it.baseline)
            assertEquals(emptyList<MarkEvent>(), it.events)
            assertEquals(current, it.snapshot)
        }
        assertTrue(bars.baseline)
        assertEquals(emptyList<MarkEvent>(), bars.events)
    }

    @Test
    fun `My ITMO scores are added or changed and a vanished score is not an event`() {
        val base = myItmo(subject(1, "Физика", score = 10.0), subject(2, "Химия", score = null))

        assertEquals(emptyList<MarkEvent>(), MarkDiff.myItmo(base, myItmo(*base.subjects.reversed().toTypedArray())).events)
        assertEquals(listOf(MARK_ADDED), kinds(base, subject(2, "Химия", score = 12.5)))
        assertEquals(listOf(MARK_ADDED), kinds(myItmo(subject(2, "Химия", score = 0.0)), subject(2, "Химия", score = 12.5)))
        assertEquals(listOf(MARK_CHANGED), kinds(myItmo(subject(3, score = 12.5)), subject(3, score = 15.0)))
        assertEquals(listOf(MARK_CHANGED), kinds(myItmo(subject(3, score = 15.0)), subject(3, score = 12.0)))
        assertEquals(emptyList<MarkEventKind>(), kinds(myItmo(subject(3, score = 15.0)), subject(3, score = null)))
        assertEquals(emptyList<MarkEventKind>(), kinds(myItmo(subject(3, score = 15.0)), subject(3, score = 0.0)))
    }

    @Test
    fun `a new final grade is an event, the same grade written differently or a vanished one is not`() {
        assertEquals(listOf(FINAL_CHANGED), kinds(myItmo(subject(1, rate = null)), subject(1, rate = "4/C")))
        assertEquals(emptyList<MarkEventKind>(), kinds(myItmo(subject(1, rate = "4/C")), subject(1, rate = "4C")))
        assertEquals(emptyList<MarkEventKind>(), kinds(myItmo(subject(1, rate = "4/C")), subject(1, rate = null)))
        assertEquals(
            listOf(MARK_CHANGED, FINAL_CHANGED),
            kinds(myItmo(subject(1, score = 60.0, rate = null)), subject(1, score = 75.0, rate = "4/C"))
        )
    }

    @Test
    fun `a new row is quiet and a vanished row is carried until it comes back`() {
        val previous = myItmo(subject(1, "Физика", score = 10.0), subject(2, "Химия", score = 20.0))

        val short = MarkDiff.myItmo(previous, myItmo(subject(1, "Физика", score = 10.0), subject(3, "Биология", score = 5.0)))
        assertEquals(emptyList<MarkEvent>(), short.events)
        assertEquals(setOf(1L, 2L, 3L), short.snapshot.subjects.map { it.entryId }.toSet())

        val back = MarkDiff.myItmo(short.snapshot, myItmo(subject(2, "Химия", score = 25.0)))
        assertEquals(listOf(MarkEvent(MarkSource.MY_ITMO, HALF, "Химия", MARK_CHANGED)), back.events)
    }

    @Test
    fun `a row recreated under another est_id is the same subject only without a second candidate`() {
        val previous = myItmo(subject(1, "Физика", score = 10.0, discipline = 7))

        val recreated = MarkDiff.myItmo(previous, myItmo(subject(2, "Физика", score = 12.0, discipline = 7)))
        assertEquals(listOf(MARK_CHANGED), recreated.events.map { it.kind })
        assertEquals(listOf(2L), recreated.snapshot.subjects.map { it.entryId })

        val twoCandidates = MarkDiff.myItmo(
            previous,
            myItmo(subject(2, "Физика", score = 12.0, discipline = 7), subject(3, "Физика", score = 14.0, discipline = 7))
        )
        assertEquals(emptyList<MarkEvent>(), twoCandidates.events)
    }

    @Test
    fun `BARS checkpoint marks are added or changed and vanished marks are carried`() {
        val previous = barsOf(plan(1, mark(10, 5.0), mark(11, 3.0)))

        assertEquals(listOf(MARK_ADDED), barsKinds(previous, plan(1, mark(10, 5.0), mark(11, 3.0), mark(12, 4.0))))
        assertEquals(listOf(MARK_CHANGED), barsKinds(previous, plan(1, mark(10, 6.0), mark(11, 3.0))))
        assertEquals(listOf(MARK_CHANGED), barsKinds(previous, plan(1, mark(10, null, absent = true), mark(11, 3.0))))
        assertEquals(listOf(MARK_ADDED), barsKinds(previous, plan(1, mark(10, 5.0), mark(11, 3.0), mark(-1, 2.0))))

        val vanished = MarkDiff.bars(previous, barsOf(plan(1, mark(10, 5.0))))
        assertEquals(emptyList<MarkEvent>(), vanished.events)
        assertEquals(setOf(10L, 11L), vanished.snapshot.plans.single().marks.map { it.id }.toSet())
    }

    @Test
    fun `a changed statement is an event unless it vanished`() {
        val previous = barsOf(plan(1, rate = null))

        assertEquals(listOf(FINAL_CHANGED), barsKinds(previous, plan(1, rate = "4/B", attempt = 1)))
        assertEquals(listOf(FINAL_CHANGED), barsKinds(barsOf(plan(1, rate = "4/B", attempt = 1)), plan(1, rate = "4/B", attempt = 2)))
        assertEquals(listOf(FINAL_CHANGED), barsKinds(previous, plan(1, absent = true)))
        assertEquals(emptyList<MarkEventKind>(), barsKinds(barsOf(plan(1, rate = "4/B", attempt = 1)), plan(1, rate = null)))
    }

    @Test
    fun `a new plan is quiet, a vanished plan is carried and empty journals stay quiet`() {
        val previous = barsOf(plan(1, mark(10, 5.0)))

        val added = MarkDiff.bars(previous, barsOf(plan(1, mark(10, 5.0)), plan(2, mark(20, 8.0))))
        assertEquals(emptyList<MarkEvent>(), added.events)

        val vanished = MarkDiff.bars(previous, barsOf(plan(2)))
        assertEquals(setOf(1L, 2L), vanished.snapshot.plans.map { it.planId }.toSet())
        assertFalse(vanished.baseline)

        assertEquals(emptyList<MarkEvent>(), MarkDiff.bars(barsOf(plan(3)), barsOf(plan(3))).events)
    }

    @Test
    fun `the order of plans and checkpoints does not matter`() {
        val previous = barsOf(plan(1, mark(10, 5.0), mark(11, 3.0)), plan(2, mark(20, 1.0)))

        val shuffled = barsOf(plan(2, mark(20, 1.0)), plan(1, mark(11, 3.0), mark(10, 5.0)))

        assertEquals(emptyList<MarkEvent>(), MarkDiff.bars(previous, shuffled).events)
    }

    @Test
    fun `events are ordered by name key, then kind`() {
        val previous = myItmo(subject(1, "Физика", score = 10.0), subject(2, "алгебра", score = null, rate = null))

        val events = MarkDiff.myItmo(
            previous,
            myItmo(subject(1, "Физика", score = 12.0, rate = "5/A"), subject(2, "алгебра", score = 4.0, rate = "3/E"))
        ).events

        assertEquals(
            listOf("алгебра" to MARK_ADDED, "алгебра" to FINAL_CHANGED, "физика" to MARK_CHANGED, "физика" to FINAL_CHANGED),
            events.map { it.nameKey to it.kind }
        )
    }

    private fun kinds(previous: MyItmoMarkSnapshot, vararg current: MyItmoSubjectMark) =
        MarkDiff.myItmo(previous, myItmo(*current)).events.map { it.kind }

    private fun barsKinds(previous: BarsMarkSnapshot, vararg current: BarsPlanMarks) =
        MarkDiff.bars(previous, barsOf(*current)).events.map { it.kind }

    private companion object {
        val HALF = StudyHalf(2026, 1)

        fun subject(
            entryId: Long,
            name: String = "Тестовый предмет $entryId",
            score: Double? = null,
            rate: String? = null,
            discipline: Long = entryId + 100
        ) = MyItmoSubjectMark(1L, 3, entryId, discipline, name, score, rate)

        fun myItmo(vararg subjects: MyItmoSubjectMark) = MyItmoMarkSnapshot(HALF, subjects.toList())

        fun mark(id: Long, value: Double?, absent: Boolean = false) = BarsCheckpointMark(id, value, absent)

        fun plan(
            id: Long,
            vararg marks: BarsCheckpointMark,
            rate: String? = null,
            attempt: Int? = null,
            absent: Boolean = false
        ) = BarsPlanMarks(id, "flow", "7", "Тестовый предмет $id", null, rate, attempt, absent, marks.toList())

        fun barsOf(vararg plans: BarsPlanMarks) = BarsMarkSnapshot(HALF, plans.toList())
    }
}
