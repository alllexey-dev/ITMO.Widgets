package dev.alllexey.itmowidgets.feature.settings.presentation

import dev.alllexey.itmowidgets.core.diagnostics.DiagnosticEntry
import dev.alllexey.itmowidgets.core.diagnostics.DiagnosticLevel
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.core.testing.RecordingDiagnostics
import dev.alllexey.itmowidgets.testkit.TestMainDispatcher
import kotlinx.datetime.LocalDateTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class DiagnosticsViewModelTest {

    private val main = TestMainDispatcher()

    @BeforeTest
    fun setUp() = main.install()

    @AfterTest
    fun tearDown() = main.reset()

    private val time = FixedAcademicTime(LocalDateTime(2026, 9, 16, 12, 0))

    @Test
    fun entriesRenderInTheAcademicZoneAndExportAsPlainText() = runTest(main.dispatcher) {
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
