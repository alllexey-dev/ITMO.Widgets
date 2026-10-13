package dev.alllexey.itmowidgets.feature.recordbook.data.bell

import dev.alllexey.itmowidgets.core.bell.BellKind
import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.navigation.AppTab
import dev.alllexey.itmowidgets.core.navigation.EntryRoute
import dev.alllexey.itmowidgets.core.navigation.RecordbookSubjectArgs
import dev.alllexey.itmowidgets.feature.recordbook.FakeBarsPreference
import dev.alllexey.itmowidgets.feature.recordbook.FakeMarkTrackingRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarkSubjectTarget
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarkValue
import dev.alllexey.itmowidgets.feature.recordbook.markNews
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class MarksBellSourceTest {

    @Test
    fun `read and unread news are rows that lead to their subject page or else the recordbook`() = runTest {
        val physics = markNews("Физика").copy(value = MarkValue(mark = 9.0, max = 10.0, delta = 9.0), read = true)
        val chemistry = markNews("Химия")
        val repository = FakeMarkTrackingRepository(physics, chemistry)
        repository.targets[physics.id] = MarkSubjectTarget(1, 3, "2026/2027", 42, bars = null)

        val items = MarksBellSource(repository, FakeBarsPreference(enabled = true)).observe().first()

        assertEquals(listOf(BellKind.MARK, BellKind.MARK), items.map { it.kind })
        assertEquals("mark:${physics.id}:${physics.detectedAt.toEpochMilliseconds()}", items[0].id)
        assertEquals("+9", items[0].lead)
        assertEquals(
            EntryRoute(AppTab.HOME, overlay = AppRoutes.RecordbookSubject(RecordbookSubjectArgs(42, 1, 3, "2026/2027"))),
            items[0].target,
        )
        assertEquals(EntryRoute(AppTab.RECORDBOOK), items[1].target)
        assertEquals(listOf(physics.id to true, chemistry.id to true), repository.targetRequests)
    }

    @Test
    fun `opening the bell reads every subject`() = runTest {
        val repository = FakeMarkTrackingRepository(markNews())

        MarksBellSource(repository, FakeBarsPreference()).markAllRead()

        assertEquals(1, repository.markAllReadCalls)
    }
}
