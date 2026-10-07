package dev.alllexey.itmowidgets.feature.recordbook.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookPeriod
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookProgram
import dev.alllexey.itmowidgets.testkit.RobolectricTestRunner
import dev.alllexey.itmowidgets.testkit.RunWith
import dev.alllexey.itmowidgets.testkit.assertTouchTargets
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The period picker of the deleted `semesterNumbersStayContinuousInPickerAndHeading` case: continuous semester
 * numbers, the selection, the program's name, the pick and the close.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class RecordbookPeriodSheetTest {

    private val program = RecordbookProgram(
        1,
        "Тестовая образовательная программа",
        listOf(
            RecordbookPeriod("2025/2026", 1, 1, false),
            RecordbookPeriod("2025/2026", 2, 1, true),
            RecordbookPeriod("2026/2027", 3, 2, false),
            RecordbookPeriod("2026/2027", 4, 2, false),
        ),
    )

    @Test
    fun theLatestSemesterComesFirstAndThePickAnswers() = runComposeUiTest {
        val picked = mutableListOf<RecordbookPeriodOption>()
        var closed = 0
        setContent {
            ItmoTheme {
                RecordbookPeriodSheetContent(
                    options = recordbookPeriodOptions(listOf(program)),
                    programName = program.name,
                    selectedProgram = 1,
                    selectedSemester = 2,
                    onSelect = { picked += it },
                    onClose = { closed++ },
                )
            }
        }

        onNodeWithText(program.name, useUnmergedTree = true).assertExists()
        val order = (4 downTo 1).map { semester ->
            val description = onNodeWithTag(RecordbookPeriodSheetTestTags.option(1, semester)).fetchSemanticsNode()
                .config[SemanticsProperties.ContentDescription].single()
            description
        }
        assertEquals(
            listOf(
                "2 курс · 4 семестр. 2026/2027",
                "2 курс · 3 семестр. 2026/2027",
                "1 курс · 2 семестр. 2025/2026 · текущий",
                "1 курс · 1 семестр. 2025/2026",
            ),
            order,
        )
        assertEquals(listOf(4, 3, 2, 1), recordbookPeriodOptions(listOf(program)).map { it.semester })
        onNodeWithTag(RecordbookPeriodSheetTestTags.option(1, 2)).assertIsSelected()
        onNodeWithTag(RecordbookPeriodSheetTestTags.option(1, 3)).assertIsNotSelected()
        assertEquals(1, onAllNodesWithTag(RecordbookPeriodSheetTestTags.CHECK, useUnmergedTree = true).fetchSemanticsNodes().size)
        assertTouchTargets()

        onNodeWithTag(RecordbookPeriodSheetTestTags.option(1, 3)).performClick()
        onNodeWithContentDescription("Закрыть").performClick()

        assertEquals(listOf(3), picked.map { it.semester })
        assertEquals(1, closed)
    }

    @Test
    fun severalProgramsNameTheProgramInEachRowInsteadOfUnderTheTitle() = runComposeUiTest {
        val other = RecordbookProgram(2, "Вторая программа", listOf(RecordbookPeriod("2025/2026", 2, 1, true)))
        setContent {
            ItmoTheme {
                RecordbookPeriodSheetContent(
                    options = recordbookPeriodOptions(listOf(program, other)),
                    programName = program.name,
                    selectedProgram = 2,
                    selectedSemester = 2,
                    onSelect = {},
                    onClose = {},
                )
            }
        }

        assertEquals(
            "1 курс · 2 семестр. 2025/2026 · текущий\nВторая программа",
            onNodeWithTag(RecordbookPeriodSheetTestTags.option(2, 2)).fetchSemanticsNode()
                .config[SemanticsProperties.ContentDescription].single(),
        )
        onNodeWithTag(RecordbookPeriodSheetTestTags.option(2, 2)).assertIsSelected()
        onNodeWithTag(RecordbookPeriodSheetTestTags.option(1, 2)).assertIsNotSelected()
    }
}
