package dev.alllexey.itmowidgets.feature.settings.ui.diagnostics

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import dev.alllexey.itmowidgets.designsystem.platform.ItmoPlatformStyle
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.settings.presentation.DiagnosticsUiState
import dev.alllexey.itmowidgets.testkit.assertNoTextOverflow
import dev.alllexey.itmowidgets.testkit.assertTouchTargets
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `DiagnosticsScreen` against the View journal it replaced: entries in the order the journal hands them (newest
 * first), copy and clear only with entries, clearing only after the confirmation, a trace that opens on a tap, and
 * text that fits at 320 dp and font scale 1.3. The clipboard and its message are the host's.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w320dp-h891dp")
class DiagnosticsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private var copies = 0
    private var clears = 0
    private var backs = 0
    private val actions = DiagnosticsActions(onBack = { backs++ }, onCopy = { copies++ }, onClear = { clears++ })

    private var state: DiagnosticsUiState by mutableStateOf(DiagnosticsUiState.Content(DiagnosticsSamples.entries))

    @Test
    fun entriesKeepTheJournalOrderAndTheirTexts() {
        show()

        val tags = compose.onAllNodesWithTag(DiagnosticsTestTags.ENTRY).fetchSemanticsNodes()
        assertEquals(DiagnosticsSamples.entries.size, tags.size)
        val tops = DiagnosticsSamples.entries.map { entry ->
            compose.onNodeWithText(entry.message, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top
        }
        assertEquals(tops.sorted(), tops)
        for (level in listOf("Падение", "Ошибка", "Предупреждение")) {
            compose.onNodeWithText(level, useUnmergedTree = true).assertExists()
        }
        compose.onNodeWithText(DiagnosticsSamples.timeLabel(DiagnosticsSamples.entries.first()), useUnmergedTree = true)
            .assertExists()
        compose.assertNoTextOverflow()
        compose.assertTouchTargets()
    }

    @Test
    fun copyAndClearWaitForEntries() {
        state = DiagnosticsUiState.Loading
        show()
        compose.onNodeWithTag(DiagnosticsTestTags.LOADING).assertExists()
        compose.onNodeWithTag(DiagnosticsTestTags.COPY).assertIsNotEnabled()
        compose.onNodeWithTag(DiagnosticsTestTags.CLEAR).assertIsNotEnabled()

        state = DiagnosticsUiState.Content(emptyList())
        compose.waitForIdle()
        compose.onNodeWithTag(DiagnosticsTestTags.EMPTY).assertExists()
        compose.onNodeWithText("Ошибок нет").assertExists()
        compose.onNodeWithTag(DiagnosticsTestTags.COPY).assertIsNotEnabled().performClick()
        compose.onNodeWithTag(DiagnosticsTestTags.CLEAR).assertIsNotEnabled().performClick()
        compose.onNode(isDialog()).assertDoesNotExist()

        state = DiagnosticsUiState.Content(DiagnosticsSamples.entries)
        compose.waitForIdle()
        compose.onNodeWithTag(DiagnosticsTestTags.COPY).assertIsEnabled().performClick()
        compose.onNodeWithTag(DiagnosticsTestTags.BACK).performClick()
        assertEquals(1, copies)
        assertEquals(1, backs)
        assertEquals(0, clears)
    }

    @Test
    fun clearingAsksFirst() {
        show()

        compose.onNodeWithTag(DiagnosticsTestTags.CLEAR).performClick()
        compose.onNodeWithText("Очистить журнал?").assertExists()
        compose.onNodeWithText("Записи удалятся с устройства, восстановить их не получится.").assertExists()
        compose.onNode(dialogButton("Отмена")).performClick()
        compose.onNode(isDialog()).assertDoesNotExist()
        assertEquals(0, clears)

        compose.onNodeWithTag(DiagnosticsTestTags.CLEAR).performClick()
        compose.onNode(dialogButton("Очистить")).performClick()
        compose.onNode(isDialog()).assertDoesNotExist()
        assertEquals(1, clears)
    }

    @Test
    fun aTraceOpensAndClosesOnATapAndAnEntryWithoutOneIsNoTarget() {
        show()
        compose.onNodeWithTag(DiagnosticsTestTags.STACK_TRACE, useUnmergedTree = true).assertDoesNotExist()

        val entries = compose.onAllNodesWithTag(DiagnosticsTestTags.ENTRY)
        entries[0].performClick()
        compose.onNodeWithTag(DiagnosticsTestTags.STACK_TRACE, useUnmergedTree = true).assertExists()
        entries[0].performClick()
        compose.onNodeWithTag(DiagnosticsTestTags.STACK_TRACE, useUnmergedTree = true).assertDoesNotExist()
        entries[2].assertHasNoClickAction()
    }

    private fun dialogButton(label: String) = hasText(label) and hasClickAction() and hasAnyAncestor(isDialog())

    private fun show() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, LARGE_FONT)) {
                ItmoTheme(platformStyle = ItmoPlatformStyle.Material) {
                    DiagnosticsScreen(state, actions, DiagnosticsSamples::timeLabel)
                }
            }
        }
        compose.waitForIdle()
    }

    private companion object {
        const val LARGE_FONT = 1.3f
    }
}
