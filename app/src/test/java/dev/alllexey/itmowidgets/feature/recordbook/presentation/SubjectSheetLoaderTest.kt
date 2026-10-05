package dev.alllexey.itmowidgets.feature.recordbook.presentation

import dev.alllexey.itmowidgets.core.resources.LinkCategory
import dev.alllexey.itmowidgets.core.resources.ResourceScope
import dev.alllexey.itmowidgets.core.resources.SubjectLinksState
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.core.testing.linksSnapshot
import dev.alllexey.itmowidgets.core.testing.subjectLink
import dev.alllexey.itmowidgets.feature.recordbook.FakeSheetScoresRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetScore
import dev.alllexey.itmowidgets.feature.recordbook.sheetScore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SubjectSheetLoaderTest {
    private val sheets = FakeSheetScoresRepository()
    private val loader = SubjectSheetLoader(sheets, FixedAcademicTime(LocalDate(2026, 9, 7)))
    private val scope = ResourceScope(1L, "Тестовый предмет", "2026-1")

    @Test fun `following the totals reads the scope once and passes on every stored change`() = runTest {
        val updates = mutableListOf<List<SheetScore>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { loader.observe(scope).collect(updates::add) }
        advanceUntilIdle()
        sheets.scores.value = listOf(sheetScore(scope = scope))
        advanceUntilIdle()
        assertEquals(listOf(scope), sheets.refreshes)
        assertEquals(listOf(emptyList(), listOf(sheetScore(scope = scope))), updates)
    }

    @Test fun `a connection of the scope wins and is dated in the academic zone`() {
        val links = SubjectLinksState.Content(linksSnapshot(shared = listOf(sheetLink("other"))))
        val connected = loader.state(scope, links, listOf(sheetScore(scope = scope))) as SubjectSheetState.Connected
        assertEquals(LocalDateTime(2026, 9, 7, 12, 0), connected.updatedAt)
        assertEquals(LocalDate(2026, 9, 7), connected.today)
    }

    @Test fun `without a connection only distinct sheet links are offered`() {
        val links = SubjectLinksState.Content(linksSnapshot(shared = listOf(
            sheetLink("sheet"),
            sheetLink("copy").copy(url = sheetUrl("sheet")),
            subjectLink("github", LinkCategory.TASKS, isMine = false).copy(url = "https://github.com/synthetic/tasks"),
        )))
        val other = sheetScore(scope = scope.copy(periodKey = "2025-2"))
        val hint = loader.state(scope, links, listOf(other)) as SubjectSheetState.Hint
        assertEquals(listOf(sheetUrl("sheet")), hint.links.map { it.url })
    }

    @Test fun `no connection and no sheet link offer nothing`() {
        assertNull(loader.state(scope, SubjectLinksState.Content(linksSnapshot()), emptyList()))
        assertNull(loader.state(scope, SubjectLinksState.Loading, emptyList()))
    }

    @Test fun `disconnecting forgets the scope`() = runTest {
        sheets.scores.value = listOf(sheetScore(scope = scope))
        loader.disconnect(scope)
        assertEquals(listOf(scope), sheets.disconnected)
        assertEquals(emptyList<SheetScore>(), sheets.scores.value)
    }

    private fun sheetLink(id: String) = subjectLink(id, LinkCategory.SCORES, isMine = false).copy(url = sheetUrl(id))

    private fun sheetUrl(id: String) = "https://docs.google.com/spreadsheets/d/1SyntheticSheet${id.padEnd(16, '0')}/edit"
}
