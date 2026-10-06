package dev.alllexey.itmowidgets.designsystem.components.expressive

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
                    options = listOf(ItmoSplitButtonOption("Эта неделя", onClick = { events += "option" })),
                    menuExpanded = false,
                    onMenuExpandedChange = { events += "menu=$it" },
                )
            }
        }

        onNodeWithText(ACTION).performClick()
        onNodeWithContentDescription(MENU).performClick()

        assertEquals(listOf("action", "menu=true"), events)
        assertTouchTargets()
    }

    private companion object {
        const val ACTION = "Экспорт в календарь"
        const val MENU = "Выбрать период"
    }
}
