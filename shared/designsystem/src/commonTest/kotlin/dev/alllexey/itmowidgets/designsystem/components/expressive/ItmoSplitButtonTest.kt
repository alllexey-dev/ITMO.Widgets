package dev.alllexey.itmowidgets.designsystem.components.expressive

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.testkit.RobolectricTestRunner
import dev.alllexey.itmowidgets.testkit.RunWith
import dev.alllexey.itmowidgets.testkit.assertTouchTargets
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class ItmoSplitButtonTest {
    @Test
    fun leadingHalfRunsTheActionAndTrailingHalfOpensTheMenu() = runComposeUiTest {
        val events = mutableListOf<String>()
        setContent {
            ItmoTheme {
                ItmoSplitButton(
                    label = ACTION,
                    onClick = { events += "action" },
                    menuLabel = MENU,
                    options = listOf(ItmoSplitButtonOption(OPTION, onClick = { events += "option" })),
                    menuExpanded = false,
                    onMenuExpandedChange = { events += "menu=$it" },
                )
            }
        }

        onNodeWithText(ACTION).performClick()
        onNodeWithContentDescription(MENU).performClick()
        assertTouchTargets()

        assertEquals(listOf("action", "menu=true"), events)
    }

    @Test
    fun anOptionClosesTheMenuBeforeItRuns() = runComposeUiTest {
        var expanded by mutableStateOf(true)
        val events = mutableListOf<String>()
        setContent {
            ItmoTheme {
                ItmoSplitButton(
                    label = ACTION,
                    onClick = {},
                    menuLabel = MENU,
                    options = listOf(ItmoSplitButtonOption(OPTION, onClick = { events += "option" })),
                    menuExpanded = expanded,
                    onMenuExpandedChange = {
                        events += "menu=$it"
                        expanded = it
                    },
                )
            }
        }

        waitForIdle()
        onNodeWithText(OPTION).performClick()
        waitForIdle()

        assertEquals(listOf("menu=false", "option"), events)
    }

    private companion object {
        const val ACTION = "Экспорт в календарь"
        const val MENU = "Выбрать период"
        const val OPTION = "Эта неделя"
    }
}
