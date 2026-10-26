package dev.alllexey.itmowidgets.designsystem.components.menus

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.testkit.RobolectricTestRunner
import dev.alllexey.itmowidgets.testkit.RunWith
import dev.alllexey.itmowidgets.testkit.assertTouchTargets
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class ItmoMenuTest {
    @Test
    fun anItemDismissesTheMenuAndThenRuns() = runComposeUiTest {
        var expanded by mutableStateOf(true)
        val events = mutableListOf<String>()
        setContent {
            ItmoTheme {
                Box {
                    Text(ANCHOR)
                    ItmoMenu(
                        expanded = expanded,
                        onDismissRequest = {
                            events += "dismiss"
                            expanded = false
                        },
                        groups = listOf(
                            ItmoMenuGroup(listOf(ItmoMenuItem(OPEN, onClick = { events += OPEN }))),
                            ItmoMenuGroup(
                                listOf(
                                    ItmoMenuItem(EDIT, onClick = { events += EDIT }, enabled = false),
                                    ItmoMenuItem(DELETE, onClick = { events += DELETE }, destructive = true),
                                ),
                            ),
                        ),
                    )
                }
            }
        }

        waitForIdle()
        onNodeWithText(EDIT).assertIsNotEnabled().performClick()
        assertTouchTargets()
        onNodeWithText(DELETE).performClick()
        waitForIdle()
        onNodeWithText(DELETE).assertDoesNotExist()
        onNodeWithText(ANCHOR).assertIsDisplayed()

        assertEquals(listOf("dismiss", DELETE), events)
    }

    @Test
    fun aTwoLineItemShowsBothLinesWithRoomAndMarksTheChoice() = runComposeUiTest {
        setContent {
            ItmoTheme {
                ItmoMenuPanel(
                    groups = listOf(
                        ItmoMenuGroup(
                            listOf(
                                ItmoMenuItem(SEMESTER, {}, supportingText = YEAR, selected = true),
                                ItmoMenuItem(OPEN, {}),
                            ),
                            label = PROGRAMME,
                        ),
                    ),
                    onDismissRequest = {},
                )
            }
        }

        onNodeWithText(PROGRAMME).assertIsDisplayed()
        onNodeWithText(YEAR).assertIsDisplayed()
        // Material's 12 dp above and below two lines: the rows of a picker never touch.
        onNodeWithText(SEMESTER).assertIsSelected().assertHeightIsAtLeast(64.dp)
    }

    private companion object {
        const val ANCHOR = "Ссылка"
        const val OPEN = "Открыть"
        const val EDIT = "Изменить"
        const val DELETE = "Удалить"
        const val PROGRAMME = "Программная инженерия"
        const val SEMESTER = "3 семестр"
        const val YEAR = "2 курс, 2026/2027"
    }
}
