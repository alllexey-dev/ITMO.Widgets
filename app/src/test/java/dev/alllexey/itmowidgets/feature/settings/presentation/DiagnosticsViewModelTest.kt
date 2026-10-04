package dev.alllexey.itmowidgets.feature.settings.presentation

import dev.alllexey.itmowidgets.core.diagnostics.DiagnosticEntry
import dev.alllexey.itmowidgets.core.diagnostics.DiagnosticLevel
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import dev.alllexey.itmowidgets.core.testing.RecordingDiagnostics
import java.time.Instant
import java.time.LocalDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DiagnosticsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val time = FixedAcademicTime(LocalDateTime.of(2026, 9, 16, 12, 0))

    @Test
    fun `entries render in the academic zone and export as plain text`() = runTest(mainDispatcherRule.dispatcher) {
        val diagnostics = RecordingDiagnostics()
        diagnostics.entries.value = listOf(
            DiagnosticEntry(Instant.parse("2026-09-16T09:04:29Z"), DiagnosticLevel.ERROR, "Sync", "failed", "java.io.IOException: timeout")
        )
        val viewModel = DiagnosticsViewModel(diagnostics, time)
        val collecting = viewModel.uiState.launchIn(backgroundScope)
        advanceUntilIdle()

        val content = viewModel.uiState.value as DiagnosticsUiState.Content
        assertEquals("2026-09-16 12:04:29", viewModel.formatTime(content.entries.single()))
        assertEquals("2026-09-16 12:04:29 ERROR Sync\nfailed\njava.io.IOException: timeout", viewModel.exportText())

        viewModel.clear()
        advanceUntilIdle()
        assertTrue((viewModel.uiState.value as DiagnosticsUiState.Content).entries.isEmpty())
        collecting.cancel()
    }
}
