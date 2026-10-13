package dev.alllexey.itmowidgets.feature.recordbook.data.today

import dev.alllexey.itmowidgets.core.navigation.ActivityRoute
import dev.alllexey.itmowidgets.core.navigation.AppTab
import dev.alllexey.itmowidgets.core.navigation.EntryRoute
import dev.alllexey.itmowidgets.core.recordbook.BarsLoginPrompt
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.core.testing.PreferenceStores
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.recordbook.FakeMarkTrackingRepository
import dev.alllexey.itmowidgets.shared.feature.recordbook.RecordbookStrings
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BarsTodoSourceTest {

    private val stores = PreferenceStores()
    private val marks = FakeMarkTrackingRepository()
    private val source = BarsTodoSource(
        stores.markSources,
        marks,
        FixedAcademicTime(LocalDateTime(LocalDate(2026, 9, 7), LocalTime(12, 0))),
    )

    @Test
    fun `nothing to do while the BARS session lives`() = runTest {
        assertTrue(source.observe().first().isEmpty())
    }

    @Test
    fun `an ended session asks to sign in again since the last BARS answer`() = runTest {
        stores.markSources.setBarsLoginPrompt(BarsLoginPrompt.SHOWN)
        marks.barsCheckedAt.value = Instant.parse("2026-09-07T06:40:00Z")

        val todo = source.observe().first().single()

        assertEquals(UiText.Res(RecordbookStrings.recordbook_todo_bars_reason_since, listOf("09:40")), todo.reason)
        assertEquals(EntryRoute(AppTab.HOME, activity = ActivityRoute.BARS_LOGIN), todo.target)

        marks.barsCheckedAt.value = Instant.parse("2026-09-05T06:40:00Z")
        assertEquals(
            UiText.Res(RecordbookStrings.recordbook_todo_bars_reason_since, listOf("5 сент., 09:40")),
            source.observe().first().single().reason,
        )
    }
}
