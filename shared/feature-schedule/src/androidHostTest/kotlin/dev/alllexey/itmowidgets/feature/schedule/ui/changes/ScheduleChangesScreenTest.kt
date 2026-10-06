package dev.alllexey.itmowidgets.feature.schedule.ui.changes

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.schedule.presentation.changes.ScheduleChangesUiState
import dev.alllexey.itmowidgets.feature.schedule.ui.changes.preview.ScheduleChangesPreviewData
import dev.alllexey.itmowidgets.testkit.RobolectricTestRunner
import dev.alllexey.itmowidgets.testkit.RunWith
import dev.alllexey.itmowidgets.testkit.assertTouchTargets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class ScheduleChangesScreenTest {

    @Test
    fun theHistoryGroupsRowsUnderDayTitlesAndReadsEachRowAsOneNode() = runComposeUiTest {
        var backs = 0
        setContent {
            ItmoTheme {
                ScheduleChangesScreen(ScheduleChangesUiState.Content(ScheduleChangesPreviewData.history), onBack = { backs++ })
            }
        }

        onNodeWithText("Сегодня").assertExists()
        assertEquals(
            "Новое. Математический анализ. Добавлена: ср, 9 сентября, 10:00. Лекция · Тестовый поток",
            description("added"),
        )
        assertEquals("Физика. Отменена: вт, 8 сентября, 08:20. Практика · ФИЗ ПИИКТ 3.2", description("cancelled"))
        assertTouchTargets()

        // Several changed fields: the summary, then one "было -> стало" line per field.
        onNodeWithTag(ScheduleChangesTestTags.LIST).performScrollToNode(hasTestTag(ScheduleChangesTestTags.row("moved")))
        assertEquals(
            "Новое. Физика. Перенесена на пт, 11 сентября, 15:20. " +
                "Время: вт, 8 сентября, 13:30 → пт, 11 сентября, 15:20. " +
                "Аудитория: 1506 · Кронва → 2202 · Ломо. Лекция · Тестовый поток",
            description("moved"),
        )
        onNodeWithText("Вчера").assertExists()

        onNodeWithTag(ScheduleChangesTestTags.LIST).performScrollToNode(hasTestTag(ScheduleChangesTestTags.row("teacher")))
        onNodeWithText("3 сентября").assertExists()
        onNodeWithText("3 сентября").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        // One changed field shows only its "было -> стало" line.
        assertTrue(description("teacher").startsWith("Новое. Математический анализ. Преподаватель: "))

        for (id in listOf("added", "cancelled", "same-day", "moved", "format", "teacher")) {
            onNodeWithTag(ScheduleChangesTestTags.LIST).performScrollToNode(hasTestTag(ScheduleChangesTestTags.row(id)))
            onNodeWithTag(ScheduleChangesTestTags.row(id))
                .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
                .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnLongClick))
        }

        onNodeWithContentDescription("Назад").performClick()
        assertEquals(1, backs)
    }

    @Test
    fun anEmptyHistorySaysSoAndLoadingStaysBlank() = runComposeUiTest {
        var state: ScheduleChangesUiState by mutableStateOf(ScheduleChangesUiState.Loading)
        setContent { ItmoTheme { ScheduleChangesScreen(state, onBack = {}) } }

        onNodeWithTag(ScheduleChangesTestTags.EMPTY).assertDoesNotExist()
        onNodeWithTag(ScheduleChangesTestTags.LIST).assertDoesNotExist()

        state = ScheduleChangesUiState.Empty
        waitForIdle()
        onNodeWithTag(ScheduleChangesTestTags.EMPTY).assertExists()
        onNodeWithText("Изменений нет").assertExists()
        onNodeWithText("За последние 30 дней").assertExists()
        assertTouchTargets()
    }

    private fun SemanticsNodeInteractionsProvider.description(id: String): String =
        onNodeWithTag(ScheduleChangesTestTags.row(id)).fetchSemanticsNode()
            .config[SemanticsProperties.ContentDescription].single()
}
