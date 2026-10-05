package dev.alllexey.itmowidgets.designsystem.components.expressive

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.testkit.RobolectricTestRunner
import dev.alllexey.itmowidgets.testkit.RunWith
import dev.alllexey.itmowidgets.testkit.assertTouchTargets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class ItmoFloatingToolbarTest {
    @Test
    fun everyActionIsALabelledTarget() = runComposeUiTest {
        val clicked = mutableListOf<String>()
        setContent {
            ItmoTheme {
                ItmoFloatingToolbar(LABELS.map { ItmoToolbarAction(it, ICON, onClick = { clicked += it }) })
            }
        }

        LABELS.forEach { onNodeWithContentDescription(it).performClick() }

        assertEquals(LABELS, clicked)
        assertTouchTargets()
    }

    @Test
    fun twoActionsAreNotAToolbar() {
        assertFailsWith<IllegalArgumentException> {
            runComposeUiTest {
                setContent {
                    ItmoTheme { ItmoFloatingToolbar(LABELS.take(2).map { ItmoToolbarAction(it, ICON, onClick = {}) }) }
                }
            }
        }
    }

    private companion object {
        val LABELS = listOf("Найти", "Добавить в календарь", "Поделиться")
        val ICON = ColorPainter(Color.Black)
    }
}
