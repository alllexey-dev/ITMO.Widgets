package dev.alllexey.itmowidgets.feature.schedule.presentation.changes

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.schedule.ScheduleChange
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import dev.alllexey.itmowidgets.core.testing.scheduleChange
import dev.alllexey.itmowidgets.core.testing.slot
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.schedule.FakeScheduleChangesRepository
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleChangesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val time = object : AcademicTimeProvider {
        override val zoneId: ZoneId = ZoneId.of("Europe/Moscow")
        override fun today(): LocalDate = LocalDate.of(2026, 9, 7)
        override fun now(): OffsetDateTime = OffsetDateTime.parse("2026-09-07T12:00:00+03:00")
    }

    @Test
    fun `the screen is blank until the file answers and says when there is nothing`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = ScheduleChangesViewModel(FakeScheduleChangesRepository(), time, SavedStateHandle())

        assertEquals(ScheduleChangesUiState.Loading, viewModel.uiState.value)
        advanceUntilIdle()
        assertEquals(ScheduleChangesUiState.Empty, viewModel.uiState.value)
    }

    @Test
    fun `changes group by the Moscow day they were found, newest first`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeScheduleChangesRepository(
            read("other", "2026-09-03T10:00:00Z"),
            // 23:30 UTC is already 02:30 of the next day in Moscow.
            read("late-night", "2026-09-06T23:30:00Z"),
            read("yesterday", "2026-09-06T10:00:00Z"),
            read("today-later-lesson", "2026-09-07T08:00:00Z", start = LocalTime.of(15, 20)),
            read("today-earlier-lesson", "2026-09-07T08:00:00Z", start = LocalTime.of(8, 20)),
            read("last-year", "2025-12-30T10:00:00Z")
        )
        val viewModel = ScheduleChangesViewModel(repository, time, SavedStateHandle())
        advanceUntilIdle()

        val days = (viewModel.uiState.value as ScheduleChangesUiState.Content).days
        assertEquals(
            listOf(
                LocalDate.of(2026, 9, 7) to RelativeDay.TODAY,
                LocalDate.of(2026, 9, 6) to RelativeDay.YESTERDAY,
                LocalDate.of(2026, 9, 3) to RelativeDay.OTHER,
                LocalDate.of(2025, 12, 30) to RelativeDay.OTHER_YEAR
            ),
            days.map { it.date to it.relative }
        )
        assertEquals(listOf("today-earlier-lesson", "today-later-lesson", "late-night"), days[0].ids())
        assertEquals(listOf("yesterday"), days[1].ids())
        assertEquals(listOf("other"), days[2].ids())
        assertEquals(0, repository.markAllReadCalls)
    }

    @Test
    fun `a visible screen marks unread changes read and keeps them new`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeScheduleChangesRepository(unread("1"), scheduleChange(id = "2", read = true))
        val viewModel = ScheduleChangesViewModel(repository, time, SavedStateHandle())
        advanceUntilIdle()
        assertEquals(emptySet<String>(), viewModel.newIds())
        assertEquals(0, repository.markAllReadCalls)

        viewModel.setVisible(true)
        advanceUntilIdle()
        assertEquals(setOf("1"), viewModel.newIds())
        assertEquals(1, repository.markAllReadCalls)
        assertEquals(true, repository.changes.value.all(ScheduleChange::read))

        repository.changes.value += unread("3")
        advanceUntilIdle()
        assertEquals(setOf("1", "3"), viewModel.newIds())
        assertEquals(2, repository.markAllReadCalls)

        viewModel.setVisible(false)
        repository.changes.value += unread("4")
        advanceUntilIdle()
        assertEquals(setOf("1", "3"), viewModel.newIds())
        assertEquals(2, repository.markAllReadCalls)

        viewModel.setVisible(true)
        advanceUntilIdle()
        assertEquals(setOf("1", "3", "4"), viewModel.newIds())
        assertEquals(3, repository.markAllReadCalls)
    }

    @Test
    fun `new rows survive recreation after everything was read`() = runTest(mainDispatcherRule.dispatcher) {
        val handle = SavedStateHandle()
        val repository = FakeScheduleChangesRepository(unread("1"), scheduleChange(id = "2", read = true))
        ScheduleChangesViewModel(repository, time, handle).setVisible(true)
        advanceUntilIdle()

        val recreated = ScheduleChangesViewModel(repository, time, handle)
        advanceUntilIdle()

        assertEquals(setOf("1"), recreated.newIds())
        val restored = ScheduleChangesViewModel(repository, time, SavedStateHandle(mapOf("new_ids" to arrayListOf("2"))))
        advanceUntilIdle()
        assertEquals(setOf("2"), restored.newIds())
    }

    private fun read(id: String, detectedAt: String, start: LocalTime = LocalTime.of(10, 0)) = scheduleChange(
        id = id,
        detectedAt = Instant.parse(detectedAt),
        after = slot(1, LocalDate.of(2026, 9, 9), start = start),
        read = true
    )

    private fun unread(id: String) = scheduleChange(id = id)

    private fun ScheduleChangeDay.ids() = rows.map { it.change.id }

    private fun ScheduleChangesViewModel.newIds(): Set<String> =
        (uiState.value as ScheduleChangesUiState.Content).days.flatMap { it.rows }.filter { it.isNew }.mapTo(mutableSetOf()) { it.change.id }
}
