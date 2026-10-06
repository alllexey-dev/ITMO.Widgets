package dev.alllexey.itmowidgets.designsystem.components.expressive

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
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
class ItmoFabMenuTest {
    @Test
    fun toggleOpensAndAnItemClosesTheMenuBeforeItRuns() = runComposeUiTest {
        val events = mutableListOf<String>()
        var expanded by mutableStateOf(false)
        setContent {
            ItmoTheme {
                ItmoFabMenu(
                    items = listOf(ItmoFabMenuItem(ITEM, ColorPainter(Color.Black), onClick = { events += "item" })),
                    expanded = expanded,
                    onExpandedChange = {
                        events += "expanded=$it"
                        expanded = it
                    },
                    icon = ColorPainter(Color.Black),
                    label = TOGGLE,
                )
            }
        }

        onNodeWithContentDescription(TOGGLE).performClick()
        waitForIdle()
        onNodeWithText(ITEM).performClick()

        assertEquals(listOf("expanded=true", "expanded=false", "item"), events)
        assertTouchTargets()
    }

    private companion object {
        const val TOGGLE = "Быстрые действия"
        const val ITEM = "QR-пропуск"
    }
}
